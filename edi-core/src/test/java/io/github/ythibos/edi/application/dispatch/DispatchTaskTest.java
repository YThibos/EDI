package io.github.ythibos.edi.application.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.port.WorkerRuntime.Launch;
import io.github.ythibos.edi.application.port.WorkerRuntime.Login;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.domain.worker.LoginName;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.testing.ExampleHome;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class DispatchTaskTest {

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void prepareAHomeWithProjects() throws IOException {
        home = WorkerFixtures.homeWithProjects(directory);
    }

    @Test
    void launchesAWorkerInATaskAddressedCopyOnAKeyedBranchFromTheOriginBase() throws IOException {
        home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));

        DispatchOutcome outcome = dispatch("fit-lamp", "implementation");

        Path worktree = home.home().root().resolve("worktrees/harness/fit-lamp");
        Launch launch = home.runtime().lastLaunch();
        assertThat(outcome.wasDeferred()).isFalse();
        assertThat(outcome.launched().worktree()).isEqualTo(worktree);
        assertThat(outcome.launched().branch()).hasToString("feature/HRN-3");
        assertThat(home.git().currentBranch(worktree)).isEqualTo("feature/HRN-3");
        assertThat(launch.worktree()).isEqualTo(worktree);
        assertThat(launch.route()).isEqualTo(new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.HIGH));
        assertThat(launch.environment()).containsEntry("EDI_ROLE", "worker").containsEntry("EDI_TASK_ID", "fit-lamp");
        assertThat(launch.firstPrompt()).startsWith("\u2063EDI_OP: brief waiting: read ").endsWith("data/tasks/fit-lamp/brief.md and follow it");
        assertThat(Files.readString(home.home().root().resolve("data/tasks/fit-lamp/brief.md"))).contains("# Brief: fit-lamp", "feature/HRN-3");
        assertThat(home.tasks().get(new TaskId("fit-lamp")).state()).isEqualTo(TaskState.IN_FLIGHT);
        assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).containsExactly("TaskDispatched");
    }

    @Test
    void runsThePipelinesAgentOnTheRouteTheRulesGiveItAtEveryLaunch() throws IOException {
        home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));
        dispatch("fit-lamp", "implementation");
        assertThat(home.pipeline().agentRoute()).isEqualTo(new Route(new ModelId("claude-opus-5-5"), EffortLevel.HIGH));

        home.runtime().closeEveryWindow();
        home.writing("config/dispatch-rules.json", ExampleHome.file("config/dispatch-rules.json")
                .replace("\"pipeline\": {\n    \"use\": { \"model\": \"claude-opus-5-5\", \"effort\": \"high\" }",
                        "\"pipeline\": {\n    \"use\": { \"model\": \"claude-sonnet-5-5\", \"effort\": \"medium\" }"));
        dispatch("fit-lamp", null);

        assertThat(home.pipeline().agentRoute()).isEqualTo(new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.MEDIUM));
    }

    @Test
    void keysThePipelinesCommitsForTheRemoteItsForgeServesWhenAShipDeliversThroughIt() throws IOException {
        home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), WorkerFixtures.queued("map-caves", TaskKind.SCOUT, "harness", "HRN-4")));
        dispatch("fit-lamp", "implementation");
        dispatch("map-caves", "research");
        assertThat(home.pipeline().keyedRemotes()).as("an origin no forge serves").isEmpty();

        home.runtime().closeEveryWindow();
        FixtureRepository.at(home.home().root().resolve("projects/harness")).run("remote", "set-url", "origin", "git@github.com:example-owner/harness.git");
        dispatch("map-caves", null);
        home.runtime().closeEveryWindow();
        dispatch("fit-lamp", null);

        assertThat(home.pipeline().keyedRemotes()).containsExactly("git@github.com:example-owner/harness.git");
    }

    @Test
    void sendsAScoutToAResearchBranch() throws IOException {
        home.storing(List.of(WorkerFixtures.queued("map-caves", TaskKind.SCOUT, "harness", "HRN-4")));

        assertThat(dispatch("map-caves", "research").launched().branch()).hasToString("research/HRN-4");
    }

    @Test
    void givesEachTaskOnTheSameKeyItsOwnBranch() throws IOException {
        home.storing(List.of(
                WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"),
                WorkerFixtures.queuedShip("fit-horn", "harness", "HRN-3"),
                WorkerFixtures.queuedShip("fit-bell", "harness", "HRN-3"),
                WorkerFixtures.queued("map-caves", TaskKind.SCOUT, "harness", "HRN-3")));

        dispatch("fit-lamp", "implementation");
        DispatchOutcome horn = dispatch("fit-horn", "implementation");
        DispatchOutcome bell = dispatch("fit-bell", "implementation");

        assertThat(horn.launched().branch()).hasToString("feature/HRN-3-1");
        assertThat(home.git().currentBranch(horn.launched().worktree())).isEqualTo("feature/HRN-3-1");
        assertThat(bell.launched().branch()).hasToString("feature/HRN-3-2");
        assertThat(dispatch("map-caves", "research").launched().branch()).hasToString("research/HRN-3");
    }

    @Test
    void keepsTheBranchOfALaunchThatFailedForItsOwnTask() throws IOException {
        home.storing(List.of(
                WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"),
                WorkerFixtures.queuedShip("fit-horn", "harness", "HRN-3")));
        home.runtime().duringTheNextLaunch(() -> {
            throw new IllegalStateException("The terminal multiplexer is gone.");
        });
        assertThatThrownBy(() -> dispatch("fit-lamp", "implementation")).hasMessageContaining("multiplexer is gone");

        DispatchOutcome horn = dispatch("fit-horn", "implementation");

        assertThat(horn.launched().branch()).hasToString("feature/HRN-3-1");
        assertThat(home.git().currentBranch(horn.launched().worktree())).isEqualTo("feature/HRN-3-1");
        assertThat(dispatch("fit-lamp", "implementation").launched().branch()).hasToString("feature/HRN-3");
    }

    @Test
    void refusesASecondWorkerWhileTheFirstIsLiveAndRelaunchesOnceItsWindowCloses() throws IOException {
        home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));
        dispatch("fit-lamp", "implementation");

        assertThatThrownBy(() -> dispatch("fit-lamp", null)).isInstanceOf(Refusal.class).hasMessageContaining("already has a live worker, fit-lamp#1");

        home.runtime().closeEveryWindow();
        Worker second = dispatch("fit-lamp", null).launched().worker();

        assertThat(second.id()).isEqualTo(new WorkerId(new TaskId("fit-lamp"), 2));
        assertThat(home.workers().get(new WorkerId(new TaskId("fit-lamp"), 1)).orElseThrow().isRetired()).isTrue();
        assertThat(home.runtime().lastLaunch().worktree()).isEqualTo(home.home().root().resolve("worktrees/harness/fit-lamp"));
    }

    @Test
    void refusesWorkThatCannotGetAWorker() throws IOException {
        home.storing(List.of(
                WorkerFixtures.queuedShip("no-key", "harness", null),
                WorkerFixtures.queued("choose-colour", TaskKind.CAPTAIN, "harness", "HRN-5")));

        assertThatThrownBy(() -> dispatch("no-key", "implementation")).isInstanceOf(Refusal.class).hasMessageContaining("has no work key");
        assertThatThrownBy(() -> dispatch("choose-colour", "implementation")).isInstanceOf(Refusal.class).hasMessageContaining("captain item");
        assertThatThrownBy(() -> dispatch("no-key", null)).isInstanceOf(Refusal.class).hasMessageContaining("names its category");
        assertThatThrownBy(() -> dispatch("no-key", "flying")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No dispatch category");
        assertThat(home.runtime().launches()).isEmpty();
    }

    @Test
    void launchesTheWorkerOfAProjectOnTheClaudeLoginItsRegistryEntryNamesOnceThatLoginIsReady() throws IOException {
        Path secondLogin = directory.resolve("logins/second");
        home = WorkerFixtures.homeWithHarnessOnTheLogin(directory.resolve("on-a-login"), "second", secondLogin);
        home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), WorkerFixtures.queuedShip("fit-mast", "EDI", "EDI-4")));

        dispatch("fit-lamp", "implementation");
        Launch onTheSecond = home.runtime().lastLaunch();
        dispatch("fit-mast", "implementation");
        Launch onTheDefault = home.runtime().lastLaunch();

        Login second = new Login(new LoginName("second"), secondLogin);
        assertThat(onTheSecond.login()).isEqualTo(second);
        assertThat(onTheDefault.login()).isEqualTo(new Login(LoginName.DEFAULT, null));
        assertThat(home.runtime().readinessChecks()).contains(second, new Login(LoginName.DEFAULT, null));
    }

    @Test
    void refusesADispatchOntoALoginThatIsNotReadyBeforeACopyABranchOrAWindowExists() throws IOException {
        home = WorkerFixtures.homeWithHarnessOnTheLogin(directory.resolve("on-a-login"), "second", directory.resolve("logins/second"));
        home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));
        String notReady = "The Claude login second is not ready for a worker: claude auth status says it is not logged in;"
                + " run CLAUDE_CONFIG_DIR=/logins/second claude auth login, then try again.";
        home.runtime().loginIsNotReady(new LoginName("second"), notReady);

        assertThatThrownBy(() -> dispatch("fit-lamp", "implementation")).isInstanceOf(Refusal.class).hasMessage(notReady);

        Path clone = home.home().root().resolve("projects/harness");
        assertThat(home.runtime().launches()).isEmpty();
        assertThat(home.home().root().resolve("worktrees/harness/fit-lamp")).doesNotExist();
        assertThat(home.dispatches().of(new TaskId("fit-lamp"))).isEmpty();
        assertThat(FixtureRepository.at(clone).run("branch", "--list", "feature/HRN-3")).isBlank();
        assertThat(home.tasks().get(new TaskId("fit-lamp")).state()).isEqualTo(TaskState.QUEUED);
    }

    private DispatchOutcome dispatch(String task, String category) {
        DispatchRequest request = new DispatchRequest(category == null ? null : new CategoryId(category), "", List.of(), null);
        return home.dispatchTask().dispatch(TaskReference.parse(task), request, CallingSession.UNNAMED, Actor.PRIMARY);
    }
}
