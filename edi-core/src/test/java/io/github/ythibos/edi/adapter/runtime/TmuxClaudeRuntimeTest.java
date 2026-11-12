package io.github.ythibos.edi.adapter.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.application.port.WorkerRuntime.Launch;
import io.github.ythibos.edi.application.port.WorkerRuntime.Login;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.LoginName;
import io.github.ythibos.edi.domain.worker.PermissionMode;
import io.github.ythibos.edi.domain.worker.WindowCheck;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.domain.worker.WorkerWindow;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.StandInScripts;
import io.github.ythibos.edi.testing.TestProcesses;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Tag("adapter")
class TmuxClaudeRuntimeTest {

    private static final String RECORDING_CLAUDE = """
            #!/bin/sh
            record="$EDI_HOME/claude-was-started"
            pwd -P > "$record.cwd"
            printf '%s\\n' "$EDI_ROLE $EDI_TASK_ID $EDI_WORKER $EDI_TOKEN_FILE ${CLAUDECODE:-no-parent-session}" > "$record.environment"
            printf '%s\\n' "${CLAUDE_CONFIG_DIR-unset}" > "$EDI_HOME/$EDI_TASK_ID.config-directory"
            for word in "$@"; do printf '%s\\0' "$word"; done > "$record.arguments"
            exec sleep 30
            """;
    private static final String RECORDING_MISE = """
            #!/bin/sh
            if [ "$1" = trust ]; then
                printf '%s: %s\\n' "$(pwd -P)" "$(cat "$(dirname "$0")/mise-trust")"
                exit 0
            fi
            printf '%s %s %s auto-install=%s trusted=%s\\n' "$1" "$2" "$3" "$MISE_EXEC_AUTO_INSTALL" "$MISE_TRUSTED_CONFIG_PATHS" > "$EDI_HOME/mise-was-entered"
            shift 2
            exec "$@"
            """;
    private static final String COMMITTING_CLAUDE = """
            #!/bin/sh
            export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1
            export GIT_AUTHOR_NAME=Worker GIT_AUTHOR_EMAIL=worker@example.org GIT_COMMITTER_NAME=Worker GIT_COMMITTER_EMAIL=worker@example.org
            git commit --quiet --allow-empty --message 'HRN-3 Fit the lamp' --message 'Co-authored-by: Claude <noreply@anthropic.com>' 2>/dev/null
            echo $? > "$EDI_HOME/commit-exit-code"
            exec sleep 30
            """;

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    private EdiHome home;
    private Path worktree;
    private Path claudeCodesHome;

    @BeforeEach
    void prepareAWorktreeAndAClaudeThatRecordsHowItStarted() throws IOException {
        assumeTrue(PrivateTmuxServer.isInstalled(), "tmux is not installed");
        home = new EdiHome(directory.resolve("home"));
        worktree = Files.createDirectories(home.worktrees().resolve("harness/fit-lamp"));
        claudeCodesHome = Files.createDirectories(directory.resolve("claude-home"));
        Files.writeString(claudeCodesHome.resolve(".claude.json"), "{\"numStartups\": 3, \"projects\": {\"/elsewhere\": {\"allowedTools\": []}}}");
        executable(directory.resolve("claude"), RECORDING_CLAUDE);
        executable(directory.resolve("mise"), RECORDING_MISE);
        Files.createDirectories(projectClone());
        Files.writeString(directory.resolve("mise-trust"), "untrusted");
    }

    @Test
    void launchesClaudeCodeInTheWorktreeWithItsSessionRouteSettingsAndEnvironment() throws IOException {
        try (PrivateTmuxServer server = PrivateTmuxServer.started(processes)) {
            TmuxClaudeRuntime runtime = server.runtime(claudeCodesHome, directory.resolve("claude").toString(), mise());
            UUID session = UUID.randomUUID();

            WorkerWindow window = runtime.launch(launch(session, List.of("WebSearch", "Bash(git push:*)")));

            assertThat(window.socket()).isEqualTo(server.socket());
            assertThat(runtime.isRunning(window)).isTrue();
            List<String> arguments = List.of(Files.readString(awaitFile(home.root().resolve("claude-was-started.arguments"))).split("\0"));
            Path settings = home.root().resolve("state/workers/fit-lamp-1/claude-settings.json");
            assertThat(arguments).containsExactly(
                    "--disallowedTools", "WebSearch", "Bash(git push:*)",
                    "--add-dir", home.taskDirectory(new TaskId("fit-lamp")).toString(),
                    "--session-id", session.toString(),
                    "--model", "claude-opus-5-5", "--effort", "high",
                    "--name", "fit-lamp",
                    "--settings", settings.toString(),
                    "--dangerously-skip-permissions",
                    "--append-system-prompt", resource("claude/worker/system-prompt.md"),
                    "--", "\u2063EDI_OP: brief waiting: read the brief and follow it");
            assertThat(Files.readString(home.root().resolve("claude-was-started.cwd")).strip()).isEqualTo(worktree.toRealPath().toString());
            assertThat(Files.readString(home.root().resolve("claude-was-started.environment")).strip())
                    .isEqualTo("worker fit-lamp fit-lamp#1 /tokens/fit-lamp-1 no-parent-session");
            JsonNode written = new JsonMapper().readTree(Files.readString(settings));
            assertThat(written.path("attribution").path("commit").asString()).isEmpty();
            assertThat(written.path("hooks").path("PreToolUse").get(0).path("hooks").get(0).path("command").asString())
                    .isEqualTo("'" + PrivateTmuxServer.STAND_IN_EDI + "' hook pre-tool-use");
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(settings))).isEqualTo("rw-------");
            JsonNode claudeConfig = new JsonMapper().readTree(Files.readString(claudeCodesHome.resolve(".claude.json")));
            assertThat(claudeConfig.path("projects").path(projectClone().toString()).path("hasTrustDialogAccepted").asBoolean()).isTrue();
            assertThat(claudeConfig.path("numStartups").asInt()).isEqualTo(3);
            assertThat(claudeConfig.path("projects").has("/elsewhere")).isTrue();

            runtime.close(window);
            assertThat(runtime.isRunning(window)).isFalse();
        }
    }

    @Test
    void startsAWorkerOfTheDefaultLoginInClaudeCodesOwnDirectoryWhateverDirectoryItsTmuxServerCarries() throws IOException {
        try (PrivateTmuxServer server = PrivateTmuxServer.startedWith(processes, Map.of("CLAUDE_CONFIG_DIR", "/the/servers/login"))) {
            TmuxClaudeRuntime runtime = server.runtime(claudeCodesHome, directory.resolve("claude").toString(), mise());

            runtime.launch(launch(UUID.randomUUID(), List.of()));

            assertThat(PrivateTmuxServer.awaitContent(home.root().resolve("fit-lamp.config-directory"), "\n").strip()).isEqualTo("unset");
        }
    }

    @Test
    void launchesTheWorkersOfTwoProjectsOnTwoLoginsEachWithItsOwnConfigurationDirectoryAndTrustEntry() throws IOException {
        Path secondLogin = Files.createDirectories(directory.resolve("logins/second"));
        Files.writeString(secondLogin.resolve(".claude.json"), "{\"hasCompletedOnboarding\": true}");
        Path gardenClone = Files.createDirectories(home.root().resolve("projects/garden"));
        Path gardenCopy = Files.createDirectories(home.worktrees().resolve("garden/paint-buoy"));
        try (PrivateTmuxServer server = PrivateTmuxServer.startedWith(processes, Map.of("CLAUDE_CONFIG_DIR", "/the/servers/login"))) {
            TmuxClaudeRuntime runtime = server.runtime(claudeCodesHome, directory.resolve("claude").toString(), mise());

            runtime.launch(launch(UUID.randomUUID(), List.of()));
            runtime.launch(launchOn(new Login(new LoginName("second"), secondLogin), WorkerId.first(new TaskId("paint-buoy")), gardenCopy, gardenClone));

            assertThat(PrivateTmuxServer.awaitContent(home.root().resolve("fit-lamp.config-directory"), "\n").strip()).isEqualTo("unset");
            assertThat(PrivateTmuxServer.awaitContent(home.root().resolve("paint-buoy.config-directory"), "\n").strip()).isEqualTo(secondLogin.toString());
            JsonNode defaultTrust = new JsonMapper().readTree(Files.readString(claudeCodesHome.resolve(".claude.json"))).path("projects");
            JsonNode secondTrust = new JsonMapper().readTree(Files.readString(secondLogin.resolve(".claude.json"))).path("projects");
            assertThat(defaultTrust.path(projectClone().toString()).path("hasTrustDialogAccepted").asBoolean()).isTrue();
            assertThat(defaultTrust.has(gardenClone.toString())).isFalse();
            assertThat(secondTrust.path(gardenClone.toString()).path("hasTrustDialogAccepted").asBoolean()).isTrue();
            assertThat(secondTrust.has(projectClone().toString())).isFalse();
        }
    }

    @Test
    void refusesALaunchOnALoginWithoutAGlobalConfigurationToRecordItsTrustIn() throws IOException {
        Path emptyLogin = Files.createDirectories(directory.resolve("logins/empty"));
        try (PrivateTmuxServer server = PrivateTmuxServer.started(processes)) {
            TmuxClaudeRuntime runtime = server.runtime(claudeCodesHome, directory.resolve("claude").toString(), mise());

            assertThatThrownBy(() -> runtime.launch(launchOn(new Login(new LoginName("empty"), emptyLogin), WorkerId.first(new TaskId("fit-lamp")), worktree,
                    projectClone())))
                    .isInstanceOf(Refusal.class).hasMessage("The Claude login empty has no global configuration " + emptyLogin.resolve(".claude.json")
                            + " to record its trust in " + projectClone() + ".");
        }
    }

    @Test
    void startsClaudeCodeOnTheToolchainTheCopyPinsWhenMiseTrustsTheProjectsCloneAndInstallsNoMissingTool() throws IOException {
        Files.writeString(directory.resolve("mise-trust"), "trusted");
        try (PrivateTmuxServer server = PrivateTmuxServer.started(processes)) {
            TmuxClaudeRuntime runtime = server.runtime(claudeCodesHome, directory.resolve("claude").toString(), mise());

            runtime.launch(launch(UUID.randomUUID(), List.of()));

            assertThat(PrivateTmuxServer.awaitContent(home.root().resolve("mise-was-entered"), "\n").strip())
                    .isEqualTo("exec -- " + directory.resolve("claude") + " auto-install=false trusted=" + trustedPathsTheCoreInherited());
            assertThat(awaitFile(home.root().resolve("claude-was-started.arguments"))).exists();
        }
    }

    @Test
    void startsClaudeCodeWithoutMiseWhenMiseDoesNotTrustTheProjectsClone() throws IOException {
        try (PrivateTmuxServer server = PrivateTmuxServer.started(processes)) {
            TmuxClaudeRuntime runtime = server.runtime(claudeCodesHome, directory.resolve("claude").toString(), mise());

            runtime.launch(launch(UUID.randomUUID(), List.of()));

            List<String> arguments = List.of(Files.readString(awaitFile(home.root().resolve("claude-was-started.arguments"))).split("\0"));
            assertThat(arguments).startsWith("--add-dir", home.taskDirectory(new TaskId("fit-lamp")).toString());
            assertThat(home.root().resolve("mise-was-entered")).doesNotExist();
        }
    }

    @Test
    void resumesTheRecordedSessionAndTellsAClosedWindowFromAChangedOrLostServer() throws IOException {
        WorkerWindow window;
        TmuxClaudeRuntime runtime;
        UUID session = UUID.randomUUID();
        try (PrivateTmuxServer server = PrivateTmuxServer.started(processes)) {
            runtime = server.runtime(claudeCodesHome, directory.resolve("claude").toString(), mise());
            window = runtime.launch(launch(session, List.of(), LaunchKind.RESUME));
            List<String> arguments = List.of(Files.readString(awaitFile(home.root().resolve("claude-was-started.arguments"))).split("\0"));
            WorkerWindow onAnotherServer = new WorkerWindow(window.socket(), window.serverPid() + 1, window.serverStartedAt(), window.windowId(), window.panePid());

            assertThat(arguments).containsSequence("--resume", session.toString()).doesNotContain("--session-id");
            assertThat(runtime.check(window)).isEqualTo(WindowCheck.OPEN);
            assertThat(runtime.check(onAnotherServer)).isEqualTo(WindowCheck.SERVER_CHANGED);
            runtime.close(window);
            assertThat(runtime.check(window)).isEqualTo(WindowCheck.WINDOW_CLOSED);
        }
        assertThat(runtime.check(window)).isEqualTo(WindowCheck.SERVER_GONE);
    }

    @Test
    void refusesAWorktreeWhoseLocalSettingsDefineHooks() throws IOException {
        Files.createDirectories(worktree.resolve(".claude"));
        Files.writeString(worktree.resolve(".claude/settings.local.json"), "{\"hooks\": {\"Stop\": [{\"hooks\": []}]}}");
        try (PrivateTmuxServer server = PrivateTmuxServer.started(processes)) {
            TmuxClaudeRuntime runtime = server.runtime(claudeCodesHome, directory.resolve("claude").toString(), mise());

            assertThatThrownBy(() -> runtime.launch(launch(UUID.randomUUID(), List.of())))
                    .isInstanceOf(Refusal.class).hasMessageContaining("defines hooks, which Claude Code would run beside the hooks a worker gets at launch");
        }
    }

    @Test
    void refusesACoAuthorCommitInTheWorkersCopyWhateverGitConfigurationTheCoreInherited() throws IOException {
        FixtureRepository copy = FixtureRepository.initialise(worktree).configure("core.hooksPath", hooksWithTheCommitMessageCheck().toString());
        executable(directory.resolve("committing-claude"), COMMITTING_CLAUDE);
        Map<String, String> coreEnvironment = Map.of("GIT_CONFIG_COUNT", "1", "GIT_CONFIG_KEY_0", "core.hooksPath",
                "GIT_CONFIG_VALUE_0", Files.createDirectories(directory.resolve("no-hooks")).toString());
        try (PrivateTmuxServer server = PrivateTmuxServer.startedWith(processes, coreEnvironment)) {
            TmuxClaudeRuntime runtime = server.runtime(claudeCodesHome, directory.resolve("committing-claude").toString(), mise());

            runtime.launch(launch(UUID.randomUUID(), List.of()));

            assertThat(PrivateTmuxServer.awaitContent(home.root().resolve("commit-exit-code"), "\n").strip()).isEqualTo("1");
            assertThat(copy.run("rev-list", "--all", "--count").strip()).isEqualTo("0");
        }
    }

    private static void executable(Path file, String script) throws IOException {
        StandInScripts.installed(file, script);
    }

    private String mise() {
        return directory.resolve("mise").toString();
    }

    private static String trustedPathsTheCoreInherited() {
        return System.getenv().getOrDefault("MISE_TRUSTED_CONFIG_PATHS", "");
    }

    private Path hooksWithTheCommitMessageCheck() throws IOException {
        Path hooks = Files.createDirectories(directory.resolve("hooks"));
        executable(hooks.resolve("commit-msg"), resource("git-hooks/commit-msg").replace("@WORK_KEY@", "HRN-3"));
        return hooks;
    }

    private Launch launch(UUID session, List<String> disallowedTools) {
        return launch(session, disallowedTools, LaunchKind.DISPATCH);
    }

    private Launch launch(UUID session, List<String> disallowedTools, LaunchKind kind) {
        return launch(session, disallowedTools, kind, new Login(LoginName.DEFAULT, null), WorkerId.first(new TaskId("fit-lamp")), worktree, projectClone());
    }

    private Launch launchOn(Login login, WorkerId worker, Path copy, Path clone) {
        return launch(UUID.randomUUID(), List.of(), LaunchKind.DISPATCH, login, worker, copy, clone);
    }

    private Launch launch(UUID session, List<String> disallowedTools, LaunchKind kind, Login login, WorkerId worker, Path copy, Path clone) {
        String task = worker.task().value();
        return new Launch(worker, kind, session, new Route(new ModelId("claude-opus-5-5"), EffortLevel.HIGH), login, copy, clone, home.taskDirectory(worker.task()),
                home.workerState(worker), Map.of("EDI_HOME", home.root().toString(), "EDI_ROLE", "worker", "EDI_TASK_ID", task,
                        "EDI_WORKER", worker.toString(), "EDI_TOKEN_FILE", "/tokens/" + task + "-1"),
                disallowedTools, PermissionMode.BYPASS, "\u2063EDI_OP: brief waiting: read the brief and follow it");
    }

    private Path projectClone() {
        return home.root().resolve("projects/harness");
    }

    private static Path awaitFile(Path file) throws IOException {
        PrivateTmuxServer.awaitContent(file, "--");
        return file;
    }

    private static String resource(String name) throws IOException {
        try (InputStream content = TmuxClaudeRuntimeTest.class.getClassLoader().getResourceAsStream(name)) {
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
