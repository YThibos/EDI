package io.github.ythibos.edi.application.wake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.store.WakeStore.QueuedWake;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.primary.SessionSource;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.domain.wake.WakeStream;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TestProcesses;
import io.github.ythibos.edi.testing.WorkerFixtures;

class WaitForWakesTest {

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    private TestHome home;
    private Worker worker;

    @BeforeEach
    void prepareAHomeWithAWorker() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
    }

    @Test
    void deliversWhatNeedsThePrimaryAndNothingRoutine() {
        WakeStream stream = followedBy(primarySession());
        home.recordStatus().report(worker.id(), StatusState.WORKING, null, null, "Fitting the lamp.");

        assertThat(home.waitForWakes().deliverTo(stream)).isEmpty();

        home.recordStatus().report(worker.id(), StatusState.NEEDS_DECISION, new StatusKey("lamp"), null, "Brass or oil?");
        WakeDelivery delivery = home.waitForWakes().deliverTo(stream).orElseThrow();

        assertThat(delivery.wakes()).extracting(queued -> queued.wake().kind()).containsExactly(WakeKind.NEEDS_DECISION);
        assertThat(delivery.ongoing()).isOne();
        assertThat(delivery.streamContinues()).isTrue();
        assertThat(delivery.wakes().getFirst().wake().detail()).isEqualTo("(lamp) Brass or oil?");
    }

    @Test
    void countsAWakeDeliveredOnceItsStreamPrintedItAndNeverAcknowledgesItForAnotherWait() {
        UUID primary = primarySession();
        WakeStream first = waitingOnceIn(primary);
        home.recordStatus().report(worker.id(), StatusState.BLOCKED, new StatusKey("lamp"), null, "The lamp is missing.");
        WakeDelivery delivery = home.waitForWakes().deliverTo(first).orElseThrow();
        home.waitForWakes().heard(primary, poll(first, delivery.numbers(), true));

        WakeStream second = waitingOnceIn(primary);

        assertThat(home.waitForWakes().deliverTo(second)).isEmpty();
        assertThat(home.wakes().unacknowledged()).extracting(QueuedWake::deliveredTo).containsExactly(primary);
    }

    @Test
    void acknowledgesAPrintedWakeOnceThePrimaryActsOnItsTask() {
        UUID primary = primarySession();
        WakeStream stream = followedBy(primary);
        home.recordStatus().report(worker.id(), StatusState.NEEDS_DECISION, new StatusKey("lamp"), null, "Brass or oil?");
        printed(primary, stream, home.waitForWakes().deliverTo(stream).orElseThrow());

        primaryActsOn("fit-lamp");

        assertThat(home.wakes().unacknowledged()).isEmpty();
    }

    @Test
    void leavesAWakeNotYetPrintedUnacknowledgedWhenThePrimaryActsOnItsTask() {
        UUID primary = primarySession();
        WakeStream stream = followedBy(primary);
        home.recordStatus().report(worker.id(), StatusState.NEEDS_DECISION, new StatusKey("lamp"), null, "Brass or oil?");
        home.waitForWakes().deliverTo(stream).orElseThrow();

        primaryActsOn("fit-lamp");

        assertThat(home.wakes().unacknowledged()).hasSize(1);
    }

    @Test
    void acknowledgesAPrintedWakeOnceTheNextWakeForItsTaskIsPrinted() {
        UUID primary = primarySession();
        WakeStream stream = followedBy(primary);
        home.recordStatus().report(worker.id(), StatusState.BLOCKED, new StatusKey("lamp"), null, "The lamp is missing.");
        printed(primary, stream, home.waitForWakes().deliverTo(stream).orElseThrow());
        home.recordStatus().report(worker.id(), StatusState.FAILED, null, null, "The lamp broke.");

        printed(primary, stream, home.waitForWakes().deliverTo(stream).orElseThrow());

        assertThat(home.wakes().unacknowledged()).extracting(queued -> queued.wake().kind()).containsExactly(WakeKind.FAILED);
    }

    @Test
    void offersAgainWhatAStreamsNextCallDidNotNameAsPrinted() {
        UUID primary = primarySession();
        WakeStream stream = followedBy(primary);
        home.recordStatus().report(worker.id(), StatusState.NEEDS_DECISION, new StatusKey("lamp"), null, "Brass or oil?");
        home.waitForWakes().deliverTo(stream).orElseThrow();

        home.waitForWakes().heard(primary, poll(stream, List.of(), false));
        WakeDelivery reoffered = home.waitForWakes().deliverTo(stream).orElseThrow();

        assertThat(reoffered.wakes()).extracting(queued -> queued.wake().kind()).containsExactly(WakeKind.NEEDS_DECISION);
        assertThat(home.wakes().unacknowledged()).extracting(QueuedWake::deliveredTo).containsOnlyNulls();
    }

    @Test
    void deliversAgainWhatAnExpiredWaitWithdrew() {
        WakeStream stream = followedBy(primarySession());
        home.recordStatus().report(worker.id(), StatusState.NEEDS_DECISION, new StatusKey("lamp"), null, "Brass or oil?");
        home.waitForWakes().withdraw(home.waitForWakes().deliverTo(stream).orElseThrow());

        WakeDelivery redelivered = home.waitForWakes().deliverTo(stream).orElseThrow();

        assertThat(redelivered.wakes()).extracting(queued -> queued.wake().kind()).containsExactly(WakeKind.NEEDS_DECISION);
    }

    @Test
    void refusesASecondStreamWhileOneIsLiveNamingTheLiveOne() {
        UUID primary = primarySession();
        WakeStream live = followedBy(primary);
        home.recordStatus().report(worker.id(), StatusState.NEEDS_DECISION, new StatusKey("lamp"), null, "Brass or oil?");

        assertThatThrownBy(() -> followedBy(primary)).isInstanceOf(Refusal.class)
                .hasMessageContaining("already live as edi wait --follow in process " + home.processes().ownId())
                .hasMessageContaining("the Monitor tool running edi wait --follow alone");
        assertThatThrownBy(() -> waitingOnceIn(primary)).isInstanceOf(Refusal.class).hasMessageContaining("already live as edi wait --follow");
        assertThat(home.waitForWakes().deliverTo(live)).isPresent();
    }

    @Test
    void replacesAStreamWhoseProcessIsGoneAndOffersItsUnprintedWakesToTheNewOne() throws IOException {
        UUID primary = primarySession();
        Process dying = processes.register(new ProcessBuilder("sleep", "30").start());
        WakeStream dead = home.waitForWakes().heard(primary, new StreamPoll(UUID.randomUUID(), dying.pid(), true, List.of(), false));
        home.recordStatus().report(worker.id(), StatusState.NEEDS_DECISION, new StatusKey("lamp"), null, "Brass or oil?");
        home.waitForWakes().deliverTo(dead).orElseThrow();
        dying.destroy();
        TestProcesses.awaitExit(dying.toHandle());

        WakeStream replacement = followedBy(primary);

        assertThat(home.waitForWakes().deliverTo(replacement).orElseThrow().wakes()).extracting(queued -> queued.wake().kind())
                .containsExactly(WakeKind.NEEDS_DECISION);
        assertThat(home.wakeStreams().liveOf(primary)).contains(replacement);
    }

    @Test
    void refusesAStreamThatHasEnded() {
        UUID primary = primarySession();
        WakeStream ended = waitingOnceIn(primary);
        home.waitForWakes().heard(primary, poll(ended, List.of(), true));

        assertThatThrownBy(() -> home.waitForWakes().heard(primary, poll(ended, List.of(), false))).isInstanceOf(Refusal.class)
                .hasMessageContaining("has ended");
        assertThat(home.wakeStreams().liveOf(primary)).isEmpty();
    }

    @Test
    void keepsTheLiveStreamAcrossARestartOfTheCore() throws IOException {
        UUID primary = primarySession();
        WakeStream stream = followedBy(primary);
        home.recordStatus().report(worker.id(), StatusState.NEEDS_DECISION, new StatusKey("lamp"), null, "Brass or oil?");
        WakeDelivery delivery = home.waitForWakes().deliverTo(stream).orElseThrow();

        TestHome restarted = home.restarted();
        WakeStream resumed = restarted.waitForWakes().heard(primary, poll(stream, delivery.numbers(), false));

        assertThat(resumed).isEqualTo(stream);
        assertThat(restarted.wakeStreams().liveOf(primary)).contains(stream);
        assertThat(restarted.waitForWakes().deliverTo(resumed)).isEmpty();
        assertThat(restarted.wakes().unacknowledged()).extracting(QueuedWake::deliveredTo).containsExactly(primary);
    }

    @Test
    void deliversAgainToANewPrimaryWhatTheLastOneNeverAcknowledged() {
        UUID crashed = primarySession();
        WakeStream stream = followedBy(crashed);
        home.recordStatus().report(worker.id(), StatusState.FAILED, null, null, "The lamp broke.");
        printed(crashed, stream, home.waitForWakes().deliverTo(stream).orElseThrow());

        UUID next = primarySession();
        WakeStream nextStream = followedBy(next);
        WakeDelivery redelivered = home.waitForWakes().deliverTo(nextStream).orElseThrow();
        printed(next, nextStream, redelivered);

        assertThat(redelivered.wakes()).extracting(QueuedWake::deliveredTo).containsExactly(crashed);
        assertThat(home.wakes().unacknowledged()).extracting(QueuedWake::deliveredTo).containsExactly(next);
    }

    @Test
    void acknowledgesWhatASessionPrintedOnceItEndsSoTheNextSessionIsOfferedNoneOfIt() {
        UUID ended = primarySession();
        WakeStream stream = followedBy(ended);
        home.recordStatus().report(worker.id(), StatusState.FAILED, null, null, "The lamp broke.");
        printed(ended, stream, home.waitForWakes().deliverTo(stream).orElseThrow());

        home.primaryHooks().sessionEnded(ended);
        WakeStream nextStream = followedBy(primarySession());

        assertThat(home.wakes().unacknowledged()).isEmpty();
        assertThat(home.waitForWakes().deliverTo(nextStream)).isEmpty();
    }

    @Test
    void servesOnlyThePrimarySession() {
        UUID primary = primarySession();
        UUID standby = UUID.randomUUID();
        home.primaryHooks().sessionStarted(standby, null, SessionSource.LAUNCH);

        home.waitForWakes().requireThePrimary(primary);
        assertThatThrownBy(() -> home.waitForWakes().requireThePrimary(standby)).isInstanceOf(Refusal.class).hasMessageContaining("serves the primary session");
        assertThatThrownBy(() -> home.waitForWakes().requireThePrimary(null)).isInstanceOf(Refusal.class);
    }

    @Test
    void gathersWakesIntoOneBatchWhileTheCaptainKeepsTheChatQuiet() {
        WakeStream stream = followedBy(primarySession());
        home.changePresence().quiet();
        home.recordStatus().report(worker.id(), StatusState.FAILED, null, null, "The lamp broke.");

        assertThat(home.waitForWakes().deliverTo(stream)).isEmpty();
        home.clock().advance(Duration.ofMinutes(29));
        home.recordStatus().report(worker.id(), StatusState.FAILED, null, null, "The spare lamp broke too.");
        assertThat(home.waitForWakes().deliverTo(stream)).isEmpty();
        home.clock().advance(Duration.ofMinutes(1));
        WakeDelivery batch = home.waitForWakes().deliverTo(stream).orElseThrow();

        assertThat(batch.wakes()).extracting(queued -> queued.wake().kind()).containsExactly(WakeKind.FAILED, WakeKind.FAILED);
        assertThat(batch.presence().keepsTheChatQuiet()).isTrue();
    }

    @Test
    void deliversAtOnceInQuietTheWakesThePrimarySettlesAloneAndKeepsTheRestForTheBatch() {
        WakeStream stream = followedBy(primarySession());
        home.changePresence().quiet();
        home.recordStatus().report(worker.id(), StatusState.FAILED, null, null, "The lamp broke.");
        home.recordStatus().report(worker.id(), StatusState.NEEDS_DECISION, new StatusKey("nm-01RUN-review"), null, "Fix the finding or skip it?");

        WakeDelivery atOnce = home.waitForWakes().deliverTo(stream).orElseThrow();
        home.waitForWakes().heard(stream.session(), new StreamPoll(stream.id(), home.processes().ownId(), true,
                atOnce.wakes().stream().map(QueuedWake::number).toList(), false));
        home.clock().advance(Duration.ofMinutes(10));
        home.recordStatus().report(worker.id(), StatusState.BLOCKED, new StatusKey("socket"), null, "The socket is missing.");
        WakeDelivery theBlock = home.waitForWakes().deliverTo(stream).orElseThrow();
        home.waitForWakes().heard(stream.session(), new StreamPoll(stream.id(), home.processes().ownId(), true,
                theBlock.wakes().stream().map(QueuedWake::number).toList(), false));
        boolean failureHeldBack = home.waitForWakes().deliverTo(stream).isEmpty();
        home.clock().advance(Duration.ofMinutes(20));
        WakeDelivery batch = home.waitForWakes().deliverTo(stream).orElseThrow();

        assertThat(atOnce.wakes()).extracting(queued -> queued.wake().kind()).containsExactly(WakeKind.NEEDS_DECISION);
        assertThat(atOnce.presence().keepsTheChatQuiet()).isTrue();
        assertThat(theBlock.wakes()).extracting(queued -> queued.wake().kind()).containsExactly(WakeKind.BLOCKED);
        assertThat(failureHeldBack).isTrue();
        assertThat(batch.wakes()).extracting(queued -> queued.wake().kind()).containsExactly(WakeKind.FAILED);
    }

    private UUID primarySession() {
        UUID session = UUID.randomUUID();
        home.primaryHooks().sessionStarted(session, null, SessionSource.LAUNCH);
        home.primaryHooks().promptSubmitted(session, "Carry on.");
        return session;
    }

    private WakeStream followedBy(UUID session) {
        return home.waitForWakes().heard(session, new StreamPoll(UUID.randomUUID(), home.processes().ownId(), true, List.of(), false));
    }

    private WakeStream waitingOnceIn(UUID session) {
        return home.waitForWakes().heard(session, new StreamPoll(UUID.randomUUID(), home.processes().ownId(), false, List.of(), false));
    }

    private static StreamPoll poll(WakeStream stream, List<Long> printed, boolean ending) {
        return new StreamPoll(stream.id(), stream.process().pid(), stream.follows(), printed, ending);
    }

    private void printed(UUID session, WakeStream stream, WakeDelivery delivery) {
        home.waitForWakes().heard(session, poll(stream, delivery.numbers(), false));
    }

    private void primaryActsOn(String task) {
        home.noteTask().note(TaskReference.parse(task), "Asked the captain.", Actor.PRIMARY);
    }
}
