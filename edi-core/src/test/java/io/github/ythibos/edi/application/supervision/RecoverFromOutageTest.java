package io.github.ythibos.edi.application.supervision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.port.WorkerRuntime.Launch;
import io.github.ythibos.edi.application.supervision.RecoverFromOutage.RouteChange;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.recovery.RecoveryAction;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import io.github.ythibos.edi.domain.worker.UsageLimit;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TestProcesses;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class RecoverFromOutageTest {

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    private TestHome home;
    private Worker lamp;
    private Worker horn;

    @BeforeEach
    void prepareAnOutage() throws IOException {
        home = WorkerFixtures.homeWithProjects(directory);
        lamp = WorkerFixtures.dispatchedShip(home, "fit-lamp", "HRN-3");
        horn = WorkerFixtures.dispatchedShip(home, "tune-horn", "HRN-4");
        home.runtime().restartTheServer();
        home.judgeWorkers().judge();
    }

    @Test
    void startsAnEntryAfreshInItsOwnCopyOnTheRouteTheCaptainNamed() {
        home.recoverFromOutage().answer(1, RecoveryAction.FRESH, new RouteChange(null, EffortLevel.MEDIUM), Actor.PRIMARY);

        Launch fresh = home.runtime().lastLaunch();
        assertThat(fresh.kind()).isEqualTo(LaunchKind.FRESH);
        assertThat(fresh.session()).isNotEqualTo(lamp.session());
        assertThat(fresh.worktree()).isEqualTo(home.home().root().resolve("worktrees/harness/fit-lamp"));
        assertThat(fresh.route()).hasToString("claude-sonnet-5-5 at medium");
        assertThat(fresh.firstPrompt()).contains("fresh start after its tmux server changed: read the handover ");
        assertThat(home.dispatches().of(new TaskId("fit-lamp")).orElseThrow().captainsOverride().reason())
                .isEqualTo("the captain's answer to number 1 on the outage hold list");
    }

    @Test
    void refusesToRelaunchWhileAUsageLimitStillHoldsALiveWorker() throws IOException {
        Worker bell = WorkerFixtures.dispatchedShip(home, "ring-bell", "HRN-5");
        home.workerHooks().sessionStarted(bell.id(), bell.session(), null);
        home.workerHooks().promptSubmitted(bell.id(), "Ring the bell.", null);
        home.runtime().transcriptEndsOn(new UsageLimit("session limit", home.clock().now().plus(Duration.ofHours(1))));
        home.workerHooks().stopFailed(bell.id(), "rate_limit", null);

        assertThatThrownBy(() -> home.recoverFromOutage().answer(2, RecoveryAction.RESUME, null, Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessageContaining("The session limit still holds");

        home.clock().advance(Duration.ofHours(1));
        home.recoverFromOutage().answer(2, RecoveryAction.RESUME, null, Actor.PRIMARY);
        assertThat(home.runtime().lastLaunch().session()).isEqualTo(horn.session());
    }

    @Test
    void keepsAnEntryOnTheListWhenItsRelaunchFails() throws IOException {
        Files.delete(home.home().root().resolve("memory/craft-rules.md"));

        assertThatThrownBy(() -> home.recoverFromOutage().answer(2, RecoveryAction.FRESH, null, Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessageContaining("Number 2 on the outage hold list could not be relaunched, so it stays on the list");

        assertThat(home.outages().numbered(2)).map(entry -> entry.task().value()).contains("tune-horn");
        assertThat(home.tasks().get(new TaskId("tune-horn")).isHeld()).isTrue();
        assertThat(home.eventTypesOf(new TaskId("tune-horn"))).contains("RecoveryChosen", "RecoveryFailed");
    }

    @Test
    void keepsTheEntryAndItsHoldUntilTheRelaunchIsRecorded() {
        home.runtime().duringTheNextLaunch(() -> {
            home.judgeWorkers().judge();
            assertThat(home.outages().open()).extracting(entry -> entry.number() + " " + entry.task()).containsExactly("1 fit-lamp", "2 tune-horn");
            assertThat(home.tasks().get(new TaskId("tune-horn")).isHeld()).isTrue();
        });

        home.recoverFromOutage().answer(2, RecoveryAction.RESUME, null, Actor.PRIMARY);

        assertThat(home.outages().open()).extracting(entry -> entry.number() + " " + entry.task()).containsExactly("1 fit-lamp");
        assertThat(home.tasks().get(new TaskId("tune-horn")).isHeld()).isFalse();
        assertThat(home.eventTypesOf(new TaskId("tune-horn"))).containsOnlyOnce("LostInOutage", "TaskReleased");
    }

    @Test
    void refusesToRelaunchACopyAProcessStillWorksIn() throws IOException {
        Path copy = home.home().root().resolve("worktrees/harness/tune-horn");
        Process survivor = processes.register(new ProcessBuilder("sleep", "30").directory(copy.toFile()).start());
        int launches = home.runtime().launches().size();

        assertThatThrownBy(() -> home.recoverFromOutage().answer(2, RecoveryAction.RESUME, null, Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessageContaining("Processes [" + survivor.pid() + "] still work in " + copy);

        assertThat(home.runtime().launches()).hasSize(launches);
        assertThat(home.outages().numbered(2)).isPresent();
        assertThat(home.tasks().get(new TaskId("tune-horn")).isHeld()).isTrue();
        survivor.destroyForcibly();
        assertThat(TestProcesses.awaitExit(survivor.toHandle())).isTrue();
    }

    @Test
    void refusesANumberThatIsNotOnTheList() {
        assertThatThrownBy(() -> home.recoverFromOutage().answer(9, RecoveryAction.RESUME, null, Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessage("The outage hold list has no number 9.");
    }

    @Test
    void countsTheTokensARecoveryRelaunchSpentWhenItsSessionEnds() {
        home.recoverFromOutage().answer(2, RecoveryAction.RESUME, null, Actor.PRIMARY);
        Worker resumed = home.workers().latestOf(new TaskId("tune-horn")).orElseThrow();
        home.runtime().transcriptSpent(new TokenUsage(10, 2000, 300, 40));

        home.workerHooks().sessionStarted(resumed.id(), resumed.session(), "/transcripts/horn.jsonl");
        home.workerHooks().sessionEnded(resumed.id(), "other");
        home.workerHooks().sessionStarted(resumed.id(), resumed.session(), null);
        home.workerHooks().sessionEnded(resumed.id(), "other");

        assertThat(home.outageList().list().recoveries()).singleElement()
                .satisfies(recovery -> assertThat(recovery.tokens()).isEqualTo(new TokenUsage(10, 2000, 300, 40)));
        assertThat(home.eventTypesOf(new TaskId("tune-horn"))).containsOnlyOnce("RecoveryCostRecorded");
    }
}
