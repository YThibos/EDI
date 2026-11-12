package io.github.ythibos.edi.adapter.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.dispatch.DispatchOutcome.Launched;
import io.github.ythibos.edi.application.dispatch.LaunchWorker;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.Dispatch;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.LoginName;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.StandInScripts;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TestProcesses;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class RelaunchOnTheRecordedLoginTest {

    private static final String CLAUDE_KEEPING_TRANSCRIPTS_PER_LOGIN = """
            #!/bin/sh
            if [ "$1" = auth ]; then exit 0; fi
            mode=; session=
            while [ $# -gt 0 ]; do
                case "$1" in
                    --session-id) mode=start; session="$2"; shift ;;
                    --resume) mode=resume; session="$2"; shift ;;
                    --) break ;;
                esac
                shift
            done
            sessions="${CLAUDE_CONFIG_DIR:-$HOME/.claude}/projects"
            mkdir -p "$EDI_HOME/launches"
            record="$EDI_HOME/launches/$EDI_WORKER"
            if [ "$mode" = resume ] && ! ls "$sessions"/*/"$session.jsonl" > /dev/null 2>&1; then
                printf '%s no-conversation %s\\n' "${CLAUDE_CONFIG_DIR-unset}" "$session" > "$record"
                exit 1
            fi
            folder="$sessions/$(pwd -P | sed 's/[^A-Za-z0-9]/-/g')"
            mkdir -p "$folder"
            printf '{"type":"user"}\\n' >> "$folder/$session.jsonl"
            printf '%s %s %s\\n' "${CLAUDE_CONFIG_DIR-unset}" "$mode" "$session" > "$record"
            """;
    private static final String MISE_TRUSTING_NOTHING = "#!/bin/sh\nexit 1\n";

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    private Path first;
    private Path second;
    private TestHome home;

    @BeforeEach
    void prepareTwoStandInLogins() throws IOException {
        assumeTrue(PrivateTmuxServer.isInstalled(), "tmux is not installed");
        first = loginDirectory("first");
        second = loginDirectory("second");
        home = WorkerFixtures.homeWithProjects(directory).withTheClaudeLogin("first", first).withTheClaudeLogin("second", second);
        StandInScripts.installed(directory.resolve("claude"), CLAUDE_KEEPING_TRANSCRIPTS_PER_LOGIN);
        StandInScripts.installed(directory.resolve("mise"), MISE_TRUSTING_NOTHING);
    }

    @Test
    void resumesAWorkerOnEachOfTwoLoginsOnTheLoginItWasLaunchedOn() throws IOException {
        WorkerFixtures.projectsOnTheLogins(home, "first", "second");
        try (PrivateTmuxServer server = PrivateTmuxServer.started(processes)) {
            LaunchWorker launcher = home.launchWorkerOn(runtimeOn(server));
            Worker lamp = launchedAndEnded(server, launcher.launch(dispatchOf(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")), null, Actor.PRIMARY));
            Worker log = launchedAndEnded(server, launcher.launch(dispatchOf(WorkerFixtures.queuedShip("keep-log", "EDI", "EDI-7")), null, Actor.PRIMARY));

            Worker lampResumed = launchedAndEnded(server, resumed(launcher, lamp));
            Worker logResumed = launchedAndEnded(server, resumed(launcher, log));

            assertThat(launchRecordOf(lamp)).isEqualTo(first + " start " + lamp.session());
            assertThat(launchRecordOf(log)).isEqualTo(second + " start " + log.session());
            assertThat(launchRecordOf(lampResumed)).isEqualTo(first + " resume " + lamp.session());
            assertThat(launchRecordOf(logResumed)).isEqualTo(second + " resume " + log.session());
            assertThat(List.of(lampResumed, logResumed)).extracting(worker -> worker.launch().kind()).containsOnly(LaunchKind.RESUME);
            assertThat(recordedLoginOf(lampResumed)).isEqualTo(new LoginName("first"));
            assertThat(recordedLoginOf(logResumed)).isEqualTo(new LoginName("second"));
        }
    }

    @Test
    void keepsAResumeOnTheOldLoginAfterItsProjectMovedAndStartsAFreshSessionOnTheNewOne() throws IOException {
        WorkerFixtures.harnessMovedToTheLogin(home, "first");
        try (PrivateTmuxServer server = PrivateTmuxServer.started(processes)) {
            LaunchWorker launcher = home.launchWorkerOn(runtimeOn(server));
            Worker kite = launchedAndEnded(server, launcher.launch(dispatchOf(WorkerFixtures.queuedShip("fly-kite", "harness", "HRN-5")), null, Actor.PRIMARY));
            WorkerFixtures.harnessMovedToTheLogin(home, "second");

            Worker resumed = launchedAndEnded(server, resumed(launcher, kite));
            Worker fresh = launchedAndEnded(server, launcher.relaunch(kite.id().task(), LaunchKind.FRESH, null, null, "the captain asked", Actor.PRIMARY));

            assertThat(launchRecordOf(resumed)).isEqualTo(first + " resume " + kite.session());
            assertThat(recordedLoginOf(resumed)).isEqualTo(new LoginName("first"));
            assertThat(launchRecordOf(fresh)).isEqualTo(second + " start " + fresh.session());
            assertThat(fresh.session()).isNotEqualTo(kite.session());
            assertThat(recordedLoginOf(fresh)).isEqualTo(new LoginName("second"));
            assertThat(transcriptsUnder(second)).containsExactly(fresh.session() + ".jsonl");
            assertThat(transcriptsUnder(first)).containsExactly(kite.session() + ".jsonl");
        }
    }

    private Path loginDirectory(String name) throws IOException {
        Path login = Files.createDirectories(directory.resolve("logins").resolve(name));
        Files.writeString(login.resolve(".claude.json"), "{\"hasCompletedOnboarding\": true}");
        return login;
    }

    private TmuxClaudeRuntime runtimeOn(PrivateTmuxServer server) {
        return server.runtime(directory.resolve("claude-home"), directory.resolve("claude").toString(), directory.resolve("mise").toString());
    }

    private Dispatch dispatchOf(Task task) {
        home.storing(List.of(task));
        return Dispatch.requested(task.id(), CategoryId.IMPLEMENTATION, BranchName.parse("feature/" + task.intake().workKey()),
                "Do the work on " + task.id() + ".", null, null, TestHome.START);
    }

    private static Launched resumed(LaunchWorker launcher, Worker earlier) {
        return launcher.relaunch(earlier.id().task(), LaunchKind.RESUME, null, null, "its window closed", Actor.PRIMARY);
    }

    private Worker launchedAndEnded(PrivateTmuxServer server, Launched launched) throws IOException {
        Worker worker = launched.worker();
        PrivateTmuxServer.awaitContent(launchRecordFile(worker), "\n");
        assertThat(server.awaitClosed(worker.window())).isTrue();
        return worker;
    }

    private String launchRecordOf(Worker worker) throws IOException {
        return Files.readString(launchRecordFile(worker)).strip();
    }

    private Path launchRecordFile(Worker worker) {
        return home.home().root().resolve("launches").resolve(worker.id().toString());
    }

    private LoginName recordedLoginOf(Worker worker) {
        return home.workers().get(worker.id()).orElseThrow().launch().login();
    }

    private static List<String> transcriptsUnder(Path login) throws IOException {
        try (Stream<Path> transcripts = Files.walk(login.resolve("projects"))) {
            return transcripts.filter(Files::isRegularFile).map(transcript -> transcript.getFileName().toString()).toList();
        }
    }
}
