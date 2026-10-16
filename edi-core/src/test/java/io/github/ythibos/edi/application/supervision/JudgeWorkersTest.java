package io.github.ythibos.edi.application.supervision;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerWindow;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class JudgeWorkersTest {

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void prepareAHome() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
    }

    @Test
    void noticesAStaleWorkerOnceUntilSomethingChanges() {
        Worker lamp = startedWorker("fit-lamp", "HRN-3");

        home.clock().advance(Duration.ofMinutes(20));
        home.judgeWorkers().judge();
        home.clock().advance(Duration.ofMinutes(20));
        home.judgeWorkers().judge();
        home.recordStatus().report(lamp.id(), StatusState.WORKING, null, null, "Still fitting the lamp.");
        home.clock().advance(Duration.ofMinutes(20));
        home.judgeWorkers().judge();

        assertThat(eventsOf("fit-lamp").stream().filter("WorkerStale"::equals)).hasSize(2);
        assertThat(wakeKinds()).containsExactly(WakeKind.WORKER_STALE, WakeKind.WORKER_STALE);
    }

    @Test
    void noticesTheEndOfADeclaredWaitAndAWorkerBusyPastTheCeilingOnce() {
        Worker lamp = startedWorker("fit-lamp", "HRN-3");
        Worker horn = startedWorker("tune-horn", "HRN-4");
        home.recordStatus().report(lamp.id(), StatusState.PAUSED, null, home.clock().now().plus(Duration.ofHours(1)), "Waiting for the pipeline run.");
        home.workerHooks().promptSubmitted(horn.id(), "Tune the horn.");

        home.clock().advance(Duration.ofHours(3));
        home.judgeWorkers().judge();
        home.clock().advance(Duration.ofHours(1));
        home.judgeWorkers().judge();

        assertThat(eventsOf("fit-lamp")).containsOnlyOnce("DeclaredWaitEnded").doesNotContain("WorkerStale");
        assertThat(eventsOf("tune-horn")).containsOnlyOnce("WorkerBusyTooLong");
    }

    @Test
    void wakesThePrimaryOnceForASingleDeadWorkerAndListsTwoOrMore() {
        Worker lamp = startedWorker("fit-lamp", "HRN-3");
        Worker horn = startedWorker("tune-horn", "HRN-4");

        home.runtime().close(lamp.window());
        home.judgeWorkers().judge();
        home.judgeWorkers().judge();

        assertThat(eventsOf("fit-lamp")).containsOnlyOnce("WorkerDied");
        assertThat(home.outages().open()).isEmpty();

        home.runtime().close(horn.window());
        home.judgeWorkers().judge();

        assertThat(home.outages().open()).extracting(entry -> entry.task().value()).containsExactly("fit-lamp", "tune-horn");
        assertThat(eventsOf("tune-horn")).contains("LostInOutage").doesNotContain("WorkerDied");
    }

    @Test
    void neverCountsAFinishedOrStoppedWorkerAsDead() {
        Worker lamp = startedWorker("fit-lamp", "HRN-3");
        Worker horn = startedWorker("tune-horn", "HRN-4");
        home.recordStatus().report(lamp.id(), StatusState.DONE, null, null, "The lamp is fitted.");
        WorkerWindow hornWindow = horn.window();

        home.runtime().close(lamp.window());
        home.runtime().close(hornWindow);
        home.workers().update(home.workers().get(horn.id()).orElseThrow().stopRequested(home.clock().now()));
        home.judgeWorkers().judge();

        assertThat(eventsOf("fit-lamp")).doesNotContain("WorkerDied", "LostInOutage");
        assertThat(eventsOf("tune-horn")).doesNotContain("WorkerDied", "LostInOutage");
    }

    @Test
    void noticesAHoldWhoseDateArrivedOnce() {
        home.storing(List.of(WorkerFixtures.queuedShip("ring-bell", "harness", "HRN-5")));
        home.holdTask().hold(new TaskReference.ById(new TaskId("ring-bell")), HoldKind.DEFERRAL, "after the lamp",
                HoldUntil.day(LocalDate.parse("2026-10-16")), Actor.PRIMARY);

        assertThat(home.noticeDueHolds().notice()).isEmpty();
        home.clock().advance(Duration.ofDays(1));
        assertThat(home.noticeDueHolds().notice()).containsExactly(new TaskId("ring-bell"));
        assertThat(home.noticeDueHolds().notice()).isEmpty();
        assertThat(wakeKinds()).containsExactly(WakeKind.HOLD_DUE);
    }

    private Worker startedWorker(String task, String key) {
        Worker worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip(task, "harness", key), CategoryId.IMPLEMENTATION);
        home.workerHooks().sessionStarted(worker.id(), worker.session(), null);
        return worker;
    }

    private List<String> eventsOf(String task) {
        return home.eventTypesOf(new TaskId(task));
    }

    private List<WakeKind> wakeKinds() {
        return home.wakes().unacknowledged().stream().map(queued -> queued.wake().kind()).toList();
    }
}
