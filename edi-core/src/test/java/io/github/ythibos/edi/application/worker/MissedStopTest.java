package io.github.ythibos.edi.application.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.store.LoggedEvent;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.UsageLimitReached;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStoppedOnAnError;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerTurnEnded;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.domain.worker.ErrorStop;
import io.github.ythibos.edi.domain.worker.LimitStop;
import io.github.ythibos.edi.domain.worker.PaneState;
import io.github.ythibos.edi.domain.worker.UsageLimit;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerHealth;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class MissedStopTest {

    private static final TaskReference LAMP = new TaskReference.ById(new TaskId("fit-lamp"));

    @TempDir
    private Path directory;

    @Test
    void wakesABusyWorkerWhoseStopEdiMissedOnceAndTellsThePrimary() throws IOException {
        TestHome home = homeWithAHarness();
        Worker lamp = busyWorker(home);
        Instant stopped = home.clock().now().plus(Duration.ofMinutes(10));
        home.clock().advance(Duration.ofHours(1));

        home.runtime().transcriptEndsOnAStopEdiMissedAt(stopped);
        assertThat(home.noticeMissedStops().notice()).extracting(Worker::id).containsExactly(lamp.id());
        assertThat(home.noticeMissedStops().notice()).isEmpty();
        home.judgeWorkers().judge();

        assertThat(healthOf(home, lamp)).isEqualTo(new WorkerHealth.Idle(stopped));
        assertThat(home.inbox().unacknowledgedOf(lamp.id().task())).extracting(InboxMessage::body).containsExactly(NoticeMissedStops.CARRY_ON);
        assertThat(home.runtime().typedLines()).containsExactly("EDI_OP: message 1 waiting: run edi inbox");
        assertThat(home.eventTypesOf(lamp.id().task())).containsOnlyOnce("WorkerStopMissed").doesNotContain("WorkerStale", "WorkerBusyTooLong");
        assertThat(home.wakes().unacknowledged()).extracting(queued -> queued.wake().kind() + " " + queued.wake().detail())
                .containsExactly("STOP_MISSED 1");
    }

    @Test
    void recordsTheEndOfAMissedTurnAtTheTimeTheTranscriptShows() throws IOException {
        TestHome home = homeWithAHarness();
        Worker lamp = busyWorker(home);
        Instant stopped = home.clock().now().plus(Duration.ofMinutes(30));
        home.clock().advance(Duration.ofHours(2));

        home.runtime().transcriptEndsOnAStopEdiMissedAt(stopped);
        home.noticeMissedStops().notice();

        assertThat(home.events().ofTask(lamp.id().task())).filteredOn(event -> event.isA(WorkerTurnEnded.class))
                .extracting(LoggedEvent::at).containsExactly(stopped);
    }

    @Test
    void tellsThePrimaryWhenTheWorkersPaneDidNotTakeTheNotice() throws IOException {
        TestHome home = homeWithAHarness();
        Worker lamp = busyWorker(home);
        home.runtime().showing(PaneState.UNKNOWN);

        home.runtime().transcriptEndsOnAStopEdiMissedAt(home.clock().now());
        home.noticeMissedStops().notice();

        assertThat(home.runtime().typedLines()).isEmpty();
        assertThat(home.inbox().unacknowledgedOf(lamp.id().task())).extracting(InboxMessage::isReceived).containsExactly(false);
        assertThat(home.wakes().unacknowledged()).extracting(queued -> queued.wake().kind()).containsExactly(WakeKind.STOP_MISSED_NOTICE_WAITING);
    }

    @Test
    void wakesEveryWorkerAndTellsThePrimaryWhenAPaneFailsItsKeystrokes() throws IOException {
        TestHome home = homeWithAHarness();
        Worker lamp = busyWorker(home);
        Worker horn = busyWorker(home, "tune-horn", "HRN-4");
        home.runtime().failingEveryKeystroke();

        home.runtime().transcriptEndsOnAStopEdiMissedAt(home.clock().now());

        assertThat(home.noticeMissedStops().notice()).extracting(Worker::id).containsExactly(lamp.id(), horn.id());
        assertThat(home.inbox().unacknowledgedOf(horn.id().task())).extracting(InboxMessage::body).containsExactly(NoticeMissedStops.CARRY_ON);
        assertThat(home.eventTypesOf(lamp.id().task())).containsOnlyOnce("WorkerStopMissed");
        assertThat(home.eventTypesOf(horn.id().task())).containsOnlyOnce("WorkerStopMissed");
        assertThat(home.wakes().unacknowledged()).extracting(queued -> queued.wake().kind())
                .containsExactly(WakeKind.STOP_MISSED_NOTICE_WAITING, WakeKind.STOP_MISSED_NOTICE_WAITING);
    }

    @Test
    void parksABusyWorkerWhoseUsageLimitStoppedItWhileTheCoreWasDownAndResumesItAtTheReset() throws IOException {
        TestHome home = homeWithAHarness();
        Worker lamp = busyWorker(home);
        UsageLimit limit = new UsageLimit("session limit", home.clock().now().plus(Duration.ofHours(2)));

        home.runtime().transcriptEndsOn(new LimitStop(home.clock().now(), limit));
        assertThat(home.noticeMissedStops().notice()).extracting(Worker::id).containsExactly(lamp.id());
        assertThat(home.noticeMissedStops().notice()).isEmpty();

        assertThat(healthOf(home, lamp)).isEqualTo(new WorkerHealth.LimitParked(limit));
        assertThat(home.inbox().unacknowledgedOf(lamp.id().task())).isEmpty();
        assertThat(home.eventTypesOf(lamp.id().task())).containsOnlyOnce("UsageLimitReached").doesNotContain("WorkerStopMissed");
        assertThat(home.wakes().unacknowledged()).extracting(queued -> queued.wake().kind()).containsExactly(WakeKind.USAGE_LIMIT);

        home.clock().advance(Duration.ofHours(2));
        assertThat(home.resumeAfterUsageLimits().resumeTheNextOneDue()).map(Worker::id).contains(lamp.id());
        assertThat(home.eventTypesOf(lamp.id().task())).containsOnlyOnce("LimitResumeTyped");
    }

    @Test
    void recordsAMissedUsageLimitAtTheTimeTheTranscriptShows() throws IOException {
        TestHome home = homeWithAHarness();
        Worker lamp = busyWorker(home);
        Instant stopped = home.clock().now().plus(Duration.ofMinutes(30));
        home.runtime().transcriptEndsOn(new LimitStop(stopped, new UsageLimit("session limit", stopped.plus(Duration.ofHours(4)))));
        home.clock().advance(Duration.ofHours(2));

        home.noticeMissedStops().notice();

        assertThat(home.events().ofTask(lamp.id().task())).filteredOn(event -> event.isA(UsageLimitReached.class))
                .extracting(LoggedEvent::at).containsExactly(stopped);
    }

    @Test
    void bringsTheSteersThatWaitedForTheMissedStop() throws IOException {
        TestHome home = homeWithAHarness();
        Worker lamp = busyWorker(home);
        home.steerWorker().steer(LAMP, "Use the brass screws.", null, Actor.PRIMARY);
        Instant stopped = home.clock().now();
        home.clock().advance(Duration.ofMinutes(30));

        home.runtime().transcriptEndsOnAStopEdiMissedAt(stopped);
        home.noticeMissedStops().notice();
        home.workerHooks().promptSubmitted(lamp.id(), home.runtime().typedLines().getLast(), null);

        assertThat(home.runtime().typedLines()).containsExactly("EDI_OP: message 2 waiting: run edi inbox");
        assertThat(home.inbox().unacknowledgedOf(lamp.id().task())).extracting(InboxMessage::body)
                .containsExactly("Use the brass screws.", NoticeMissedStops.CARRY_ON);
        assertThat(healthOf(home, lamp)).isInstanceOf(WorkerHealth.Busy.class);
    }

    @Test
    void recordsALimitOnceWhenItsStopFailureHookArrivesAfterTheRoundParkedTheWorker() throws IOException {
        TestHome home = homeWithAHarness();
        Worker lamp = busyWorker(home);
        UsageLimit limit = new UsageLimit("session limit", home.clock().now().plus(Duration.ofHours(2)));
        home.runtime().transcriptEndsOn(new LimitStop(home.clock().now(), limit));

        home.noticeMissedStops().notice();
        home.clock().advance(Duration.ofSeconds(1));
        home.workerHooks().stopFailed(lamp.id(), "rate_limit", null);

        assertThat(healthOf(home, lamp)).isEqualTo(new WorkerHealth.LimitParked(limit));
        assertThat(home.eventTypesOf(lamp.id().task())).containsOnlyOnce("UsageLimitReached");
        assertThat(home.wakes().unacknowledged()).extracting(queued -> queued.wake().kind()).containsExactly(WakeKind.USAGE_LIMIT);
    }

    @Test
    void wakesAWorkerWhoseTurnEndedOnAnApiErrorOnceTheHookGraceHasPassed() throws IOException {
        TestHome home = homeWithAHarness();
        Worker lamp = busyWorker(home);
        Instant ended = home.clock().now();
        home.runtime().transcriptEndsOn(new ErrorStop(ended, "overloaded"));
        home.clock().advance(NoticeMissedStops.HOOK_GRACE);

        assertThat(home.noticeMissedStops().notice()).extracting(Worker::id).containsExactly(lamp.id());
        assertThat(home.noticeMissedStops().notice()).isEmpty();

        assertThat(healthOf(home, lamp)).isEqualTo(new WorkerHealth.Idle(ended));
        assertThat(home.runtime().typedLines()).containsExactly("EDI_OP: message 1 waiting: run edi inbox");
        assertThat(home.eventTypesOf(lamp.id().task())).containsOnlyOnce("WorkerStoppedOnAnError", "WorkerStopMissed");
        assertThat(home.events().ofTask(lamp.id().task())).filteredOn(event -> event.isA(WorkerStoppedOnAnError.class))
                .extracting(LoggedEvent::at).containsExactly(ended);
        assertThat(home.wakes().unacknowledged()).extracting(queued -> queued.wake().kind()).containsExactly(WakeKind.STOP_MISSED);
    }

    @Test
    void leavesAWorkerWhoseTurnEndedOnAnApiErrorBusyWithinTheHookGrace() throws IOException {
        TestHome home = homeWithAHarness();
        Worker lamp = busyWorker(home);
        home.runtime().transcriptEndsOn(new ErrorStop(home.clock().now(), "overloaded"));
        home.clock().advance(NoticeMissedStops.HOOK_GRACE.minusSeconds(1));

        assertThat(home.noticeMissedStops().notice()).isEmpty();

        assertThat(healthOf(home, lamp)).isInstanceOf(WorkerHealth.Busy.class);
        assertThat(home.runtime().typedLines()).isEmpty();
        assertThat(home.eventTypesOf(lamp.id().task())).doesNotContain("WorkerStoppedOnAnError", "WorkerStopMissed");
    }

    @Test
    void leavesAWorkerAloneWhoseStopFailureHookArrivedFirst() throws IOException {
        TestHome home = homeWithAHarness();
        Worker lamp = busyWorker(home);
        home.runtime().transcriptEndsOn(new ErrorStop(home.clock().now(), "overloaded"));
        home.workerHooks().stopFailed(lamp.id(), "overloaded", null);
        home.clock().advance(NoticeMissedStops.HOOK_GRACE);

        assertThat(home.noticeMissedStops().notice()).isEmpty();

        assertThat(home.runtime().typedLines()).isEmpty();
        assertThat(home.eventTypesOf(lamp.id().task())).containsOnlyOnce("WorkerStoppedOnAnError").doesNotContain("WorkerStopMissed");
        assertThat(home.wakes().unacknowledged()).isEmpty();
    }

    @Test
    void leavesABusyWorkerWhoseTranscriptShowsNoMissedStopAtWork() throws IOException {
        TestHome home = homeWithAHarness();
        Worker lamp = busyWorker(home);

        assertThat(home.noticeMissedStops().notice()).isEmpty();

        assertThat(healthOf(home, lamp)).isInstanceOf(WorkerHealth.Busy.class);
        assertThat(home.runtime().typedLines()).isEmpty();
    }

    @Test
    void ignoresAMissedStopFromBeforeTheTurnEdiKnows() throws IOException {
        TestHome home = homeWithAHarness();
        Instant earlierTurn = home.clock().now();
        home.clock().advance(Duration.ofMinutes(5));
        Worker lamp = busyWorker(home);

        home.runtime().transcriptEndsOnAStopEdiMissedAt(earlierTurn);

        assertThat(home.noticeMissedStops().notice()).isEmpty();
        assertThat(healthOf(home, lamp)).isInstanceOf(WorkerHealth.Busy.class);
    }

    @Test
    void leavesAnIdleWorkerAlone() throws IOException {
        TestHome home = homeWithAHarness();
        Worker lamp = busyWorker(home);
        home.workerHooks().stopping(lamp.id());

        home.runtime().transcriptEndsOnAStopEdiMissedAt(home.clock().now());

        assertThat(home.noticeMissedStops().notice()).isEmpty();
        assertThat(home.runtime().typedLines()).isEmpty();
    }

    private TestHome homeWithAHarness() throws IOException {
        return TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
    }

    private static Worker busyWorker(TestHome home) {
        return busyWorker(home, "fit-lamp", "HRN-3");
    }

    private static Worker busyWorker(TestHome home, String task, String key) {
        Worker worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip(task, "harness", key), CategoryId.IMPLEMENTATION);
        home.workerHooks().sessionStarted(worker.id(), worker.session(), "/transcripts/" + task + ".jsonl");
        home.workerHooks().promptSubmitted(worker.id(), "Work on " + task + ".", null);
        return worker;
    }

    private static WorkerHealth healthOf(TestHome home, Worker worker) {
        return home.workerList().judged(home.workers().get(worker.id()).orElseThrow()).health();
    }
}
