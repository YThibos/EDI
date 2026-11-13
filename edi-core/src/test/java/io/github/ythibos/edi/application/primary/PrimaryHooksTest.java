package io.github.ythibos.edi.application.primary;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.wake.StreamPoll;
import io.github.ythibos.edi.application.worker.HookAnswer;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.primary.PrimarySession;
import io.github.ythibos.edi.domain.primary.PrimaryWait;
import io.github.ythibos.edi.domain.primary.SessionProcess;
import io.github.ythibos.edi.domain.primary.SessionSource;
import io.github.ythibos.edi.domain.primary.Standing;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.worker.LimitStop;
import io.github.ythibos.edi.domain.worker.UsageLimit;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.Cards;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TestProcesses;
import io.github.ythibos.edi.testing.WorkerFixtures;

class PrimaryHooksTest {

    private static final StatusKey LAMP_CHOICE = new StatusKey("lamp-choice");

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void prepareAHome() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
    }

    @Test
    void givesANewSessionTheRoleOnlyWhenNoLiveSessionHoldsIt() throws IOException {
        Process running = processes.register(new ProcessBuilder("sleep", "30").start());
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        home.primaryHooks().sessionStarted(first, running.pid(), SessionSource.LAUNCH);
        home.primaryHooks().sessionStarted(second, null, SessionSource.LAUNCH);

        assertThat(home.primarySessions().standingOf(first)).isEqualTo(Standing.PRIMARY);
        assertThat(home.primarySessions().standingOf(second)).isEqualTo(Standing.STANDBY);

        running.destroyForcibly();
        TestProcesses.awaitExit(running.toHandle());
        UUID third = UUID.randomUUID();
        home.primaryHooks().sessionStarted(third, null, SessionSource.LAUNCH);

        assertThat(home.primarySessions().standingOf(third)).isEqualTo(Standing.PRIMARY);
        assertThat(home.primarySessions().standingOf(first)).isEqualTo(Standing.STANDBY);
    }

    @Test
    void keepsTheRoleOfALiveSessionAcrossACoreRestartUntilAnotherProcessReusesItsId() throws IOException {
        Process claude = processes.register(new ProcessBuilder("sleep", "30").start());
        UUID primary = UUID.randomUUID();
        home.primaryHooks().sessionStarted(primary, claude.pid(), SessionSource.LAUNCH);
        TestHome restarted = home.restarted();

        assertThat(restarted.primarySessions().standingOf(primary)).isEqualTo(Standing.PRIMARY);

        recordTheStartOneTickLater(restarted, primary);

        assertThat(restarted.primarySessions().standingOf(primary)).isEqualTo(Standing.UNCLAIMED);
        claude.destroyForcibly();
        assertThat(TestProcesses.awaitExit(claude.toHandle())).isTrue();
    }

    @Test
    void keepsTheRoleWhenTheSameSessionStartsAgainAndFreesItWhenTheSessionEnds() {
        UUID primary = UUID.randomUUID();
        UUID standby = UUID.randomUUID();
        home.primaryHooks().sessionStarted(primary, null, SessionSource.LAUNCH);
        home.primaryHooks().sessionStarted(standby, null, SessionSource.LAUNCH);
        home.primaryHooks().sessionStarted(primary, null, SessionSource.LAUNCH);

        assertThat(home.primarySessions().standingOf(primary)).isEqualTo(Standing.PRIMARY);

        home.primaryHooks().sessionEnded(primary);

        assertThat(home.primarySessions().standingOf(standby)).isEqualTo(Standing.UNCLAIMED);
    }

    @Test
    void endsATurnWithWorkInFlightOnlyWhileAWakeStreamIsLive() {
        WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        UUID primary = UUID.randomUUID();
        home.primaryHooks().sessionStarted(primary, null, SessionSource.LAUNCH);
        home.primaryHooks().promptSubmitted(primary, "How is the lamp?");

        HookAnswer blind = home.primaryHooks().stopping(primary);
        streamIn(primary, home.processes().ownId(), true);
        HookAnswer streaming = home.primaryHooks().stopping(primary);

        assertThat(blind.verdict()).isEqualTo(HookAnswer.Verdict.BLOCK_THE_STOP);
        assertThat(blind.reason()).isEqualTo("Work is in flight and no wake stream is live: start edi wait --follow alone with the Monitor tool, then stop.");
        assertThat(streaming).isEqualTo(HookAnswer.PROCEED);
    }

    @Test
    void holdsTheTurnWhileAnAnsweredDecisionLeavesItsWorkersQuestionUnresolved() {
        Worker worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        UUID primary = UUID.randomUUID();
        home.primaryHooks().sessionStarted(primary, null, SessionSource.LAUNCH);
        home.primaryHooks().promptSubmitted(primary, "How is the lamp?");
        streamIn(primary, home.processes().ownId(), true);
        home.recordStatus().report(worker.id(), StatusState.NEEDS_DECISION, LAMP_CHOICE, null, "Brass or oil?");
        recordAnAnswerThatNeverReachedTheWorker(home.raiseDecision().raise(TaskReference.parse("fit-lamp"), Cards.card("Brass or oil?"), Actor.PRIMARY));
        home.primaryHooks().reportRendered(primary);

        HookAnswer held = home.primaryHooks().stopping(primary);
        home.steerWorker().steer(TaskReference.parse("fit-lamp"), "Use the oil lamp.", LAMP_CHOICE, Actor.PRIMARY);
        home.primaryHooks().reportRendered(primary);
        HookAnswer carried = home.primaryHooks().stopping(primary);

        assertThat(held.verdict()).isEqualTo(HookAnswer.Verdict.BLOCK_THE_STOP);
        assertThat(held.reason()).isEqualTo("D1 is answered and the worker of fit-lamp still waits on its question lamp-choice:"
                + " carry the answer with edi steer fit-lamp --resolves lamp-choice. Then stop.");
        assertThat(carried).isEqualTo(HookAnswer.PROCEED);
    }

    @Test
    void doesNotHoldTheTurnForAQuestionAskedAfterTheAnsweredDecisionWasRaised() {
        Worker worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        UUID primary = UUID.randomUUID();
        home.primaryHooks().sessionStarted(primary, null, SessionSource.LAUNCH);
        home.primaryHooks().promptSubmitted(primary, "How is the lamp?");
        streamIn(primary, home.processes().ownId(), true);
        home.recordStatus().report(worker.id(), StatusState.NEEDS_DECISION, LAMP_CHOICE, null, "Brass or oil?");
        DecisionNumber raised = home.raiseDecision().raise(TaskReference.parse("fit-lamp"), Cards.card("Brass or oil?"), Actor.PRIMARY);
        home.clock().advance(Duration.ofMinutes(5));
        home.recordStatus().report(worker.id(), StatusState.BLOCKED, new StatusKey("nm-4-review"), null, "The review asks about the wiring.");
        home.clock().advance(Duration.ofMinutes(5));
        recordAnAnswerThatNeverReachedTheWorker(raised);
        home.primaryHooks().reportRendered(primary);

        assertThat(home.primaryHooks().stopping(primary)).isEqualTo(HookAnswer.PROCEED);
    }

    @Test
    void seesTheWakeStreamLiveUntilItsProcessIsGone() throws IOException {
        WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        UUID primary = UUID.randomUUID();
        home.primaryHooks().sessionStarted(primary, null, SessionSource.LAUNCH);
        home.primaryHooks().promptSubmitted(primary, "How is the lamp?");
        PrimaryWait beforeAnyStream = home.primaryWaits().current();
        Process stream = processes.register(new ProcessBuilder("sleep", "30").start());
        streamIn(primary, stream.pid(), true);
        Instant started = home.clock().now();
        home.clock().advance(Duration.ofMinutes(5));

        PrimaryWait following = home.primaryWaits().current();
        stream.destroy();
        TestProcesses.awaitExit(stream.toHandle());
        PrimaryWait afterItDied = home.primaryWaits().current();

        assertThat(beforeAnyStream).isEqualTo(new PrimaryWait(PrimaryWait.Sighting.MISSING, null, 1));
        assertThat(following).isEqualTo(new PrimaryWait(PrimaryWait.Sighting.FOLLOWING, started, 1));
        assertThat(afterItDied).isEqualTo(new PrimaryWait(PrimaryWait.Sighting.MISSING, null, 1));
        assertThat(afterItDied.needsAWait()).isTrue();
        assertThat(home.primaryHooks().stopping(primary).verdict()).isEqualTo(HookAnswer.Verdict.BLOCK_THE_STOP);
    }

    @Test
    void tellsAWaitForOneWakeFromTheWakeStream() {
        UUID primary = UUID.randomUUID();
        home.primaryHooks().sessionStarted(primary, null, SessionSource.LAUNCH);
        home.primaryHooks().promptSubmitted(primary, "How is the lamp?");
        streamIn(primary, home.processes().ownId(), false);

        assertThat(home.primaryWaits().current().sighting()).isEqualTo(PrimaryWait.Sighting.WAITING_ONCE);
        assertThat(home.primaryWaits().current().needsAWait()).isFalse();
    }

    @Test
    void neverHoldsAStandbySessionsStopOrBlocksOneTurnMoreThanThreeTimes() {
        WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        UUID primary = UUID.randomUUID();
        UUID standby = UUID.randomUUID();
        home.primaryHooks().sessionStarted(primary, null, SessionSource.LAUNCH);
        home.primaryHooks().sessionStarted(standby, null, SessionSource.LAUNCH);
        home.primaryHooks().promptSubmitted(standby, "<task-notification>done</task-notification>");
        home.primaryHooks().promptSubmitted(primary, "How is the lamp?");

        assertThat(home.primaryHooks().stopping(standby)).isEqualTo(HookAnswer.PROCEED);
        for (int block = 0; block < 3; block++) {
            assertThat(home.primaryHooks().stopping(primary).verdict()).isEqualTo(HookAnswer.Verdict.BLOCK_THE_STOP);
        }
        assertThat(home.primaryHooks().stopping(primary)).isEqualTo(HookAnswer.PROCEED);
    }

    @Test
    void tellsTheNextSessionThatThePreviousPrimaryStoppedOnItsUsageLimit() throws IOException {
        Instant reset = home.clock().now().plus(Duration.ofHours(2));
        home.runtime().transcriptEndsOn(new LimitStop(home.clock().now(), new UsageLimit("session limit", reset)));
        UUID stopped = UUID.randomUUID();
        home.primaryHooks().sessionStarted(stopped, null, SessionSource.LAUNCH);
        home.primaryHooks().stopFailed(stopped, "/transcripts/stopped.jsonl");
        home.primaryHooks().sessionEnded(stopped);

        home.clock().advance(Duration.ofMinutes(1));
        UUID next = UUID.randomUUID();
        home.primaryHooks().sessionStarted(next, null, SessionSource.LAUNCH);
        home.clock().advance(Duration.ofMinutes(1));
        UUID later = UUID.randomUUID();
        home.primaryHooks().sessionStarted(later, null, SessionSource.LAUNCH);

        assertThat(home.sessionStartDigest().digestFor(next).previousLimitStop().limit()).isEqualTo(new UsageLimit("session limit", reset));
        assertThat(home.sessionStartDigest().digestFor(later).previousLimitStop()).isNull();
    }

    private void recordAnAnswerThatNeverReachedTheWorker(DecisionNumber number) {
        CaptainAnswer answer = new CaptainAnswer("B", new OptionKey('B'), "", home.clock().now(), AnswerChannel.CONSOLE);
        home.decisions().save(home.decisions().get(number).answered(answer));
    }

    private void streamIn(UUID session, long pid, boolean follows) {
        home.waitForWakes().heard(session, new StreamPoll(UUID.randomUUID(), pid, follows, List.of(), false));
    }

    private static void recordTheStartOneTickLater(TestHome home, UUID session) {
        PrimarySession recorded = home.primarySessionStore().get(session).orElseThrow();
        SessionProcess reused = new SessionProcess(recorded.process().pid(), recorded.process().startTick() + 1);
        home.primarySessionStore().save(recorded.restarted(reused));
    }
}
