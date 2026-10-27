package io.github.ythibos.edi.application.supervision;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.LimitStop;
import io.github.ythibos.edi.domain.worker.PaneState;
import io.github.ythibos.edi.domain.worker.UsageLimit;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerHealth;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class UsageLimitsTest {

    @TempDir
    private Path directory;

    private TestHome home;
    private Instant reset;

    @BeforeEach
    void prepareAHome() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        reset = home.clock().now().plus(Duration.ofHours(2));
    }

    @Test
    void parksAWorkerWhoseTranscriptEndsOnItsLimitAndRecordsAnyOtherStopFailure() {
        Worker lamp = busyWorker("fit-lamp", "HRN-3");
        Worker horn = busyWorker("tune-horn", "HRN-4");

        home.workerHooks().stopFailed(horn.id(), "server_error", "/transcripts/horn.jsonl");
        home.runtime().transcriptEndsOn(new LimitStop(home.clock().now(), new UsageLimit("session limit", reset)));
        home.workerHooks().stopFailed(lamp.id(), "rate_limit", "/transcripts/lamp.jsonl");

        assertThat(healthOf(lamp)).isEqualTo(new WorkerHealth.LimitParked(new UsageLimit("session limit", reset)));
        assertThat(healthOf(horn)).isInstanceOf(WorkerHealth.Idle.class);
        assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).contains("UsageLimitReached");
        assertThat(home.eventTypesOf(new TaskId("tune-horn"))).contains("WorkerStoppedOnAnError").doesNotContain("UsageLimitReached");
    }

    @Test
    void typesOneResumeLineAfterTheResetAndOnlyOneWorkerAMinute() {
        Worker lamp = parkedWorker("fit-lamp", "HRN-3");
        Worker horn = parkedWorker("tune-horn", "HRN-4");

        assertThat(home.resumeAfterUsageLimits().resumeTheNextOneDue()).isEmpty();
        home.clock().advance(Duration.ofHours(2));
        assertThat(home.resumeAfterUsageLimits().resumeTheNextOneDue()).map(Worker::id).contains(lamp.id());
        assertThat(home.resumeAfterUsageLimits().resumeTheNextOneDue()).isEmpty();
        home.clock().advance(Duration.ofMinutes(1));
        assertThat(home.resumeAfterUsageLimits().resumeTheNextOneDue()).map(Worker::id).contains(horn.id());
        home.clock().advance(Duration.ofMinutes(5));
        assertThat(home.resumeAfterUsageLimits().resumeTheNextOneDue()).isEmpty();

        assertThat(home.runtime().typedLines()).containsExactly(ResumeAfterUsageLimits.RESUME, ResumeAfterUsageLimits.RESUME);
        assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).containsOnlyOnce("LimitResumeTyped");
    }

    @Test
    void typesNothingIntoADialogAndSaysSoOnce() {
        Worker lamp = parkedWorker("fit-lamp", "HRN-3");
        home.runtime().showing(PaneState.DIALOG_CONTINUE_AFTER_RESET);

        home.clock().advance(Duration.ofHours(2));
        home.resumeAfterUsageLimits().resumeTheNextOneDue();
        home.clock().advance(Duration.ofMinutes(5));
        home.resumeAfterUsageLimits().resumeTheNextOneDue();

        assertThat(home.runtime().typedLines()).isEmpty();
        assertThat(home.eventTypesOf(lamp.id().task())).containsOnlyOnce("LimitResumeHeldBack");
    }

    @Test
    void leavesAWorkerThatContinuedOnItsOwnAlone() {
        Worker lamp = parkedWorker("fit-lamp", "HRN-3");
        home.runtime().showing(PaneState.BUSY);

        home.clock().advance(Duration.ofHours(2));
        home.resumeAfterUsageLimits().resumeTheNextOneDue();

        assertThat(home.runtime().typedLines()).isEmpty();
        assertThat(home.eventTypesOf(lamp.id().task())).doesNotContain("LimitResumeTyped", "LimitResumeHeldBack");
    }

    private Worker busyWorker(String task, String key) {
        Worker worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip(task, "harness", key), CategoryId.IMPLEMENTATION);
        home.workerHooks().sessionStarted(worker.id(), worker.session(), null);
        home.workerHooks().promptSubmitted(worker.id(), "Work on " + task + ".", null);
        return worker;
    }

    private Worker parkedWorker(String task, String key) {
        Worker worker = busyWorker(task, key);
        home.runtime().transcriptEndsOn(new LimitStop(home.clock().now(), new UsageLimit("session limit", reset)));
        home.workerHooks().stopFailed(worker.id(), "rate_limit", null);
        return worker;
    }

    private WorkerHealth healthOf(Worker worker) {
        return home.workerList().judged(home.workers().get(worker.id()).orElseThrow()).health();
    }
}
