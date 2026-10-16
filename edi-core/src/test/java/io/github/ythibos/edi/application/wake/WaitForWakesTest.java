package io.github.ythibos.edi.application.wake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.store.WakeStore.QueuedWake;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class WaitForWakesTest {

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
        UUID primary = primarySession();
        home.recordStatus().report(worker.id(), StatusState.WORKING, null, null, "Fitting the lamp.");

        assertThat(home.waitForWakes().deliverTo(primary)).isEmpty();

        home.recordStatus().report(worker.id(), StatusState.NEEDS_DECISION, new StatusKey("lamp"), null, "Brass or oil?");
        WakeDelivery delivery = home.waitForWakes().deliverTo(primary).orElseThrow();

        assertThat(delivery.wakes()).extracting(queued -> queued.wake().kind()).containsExactly(WakeKind.NEEDS_DECISION);
        assertThat(delivery.ongoing()).isOne();
        assertThat(delivery.wakes().getFirst().wake().detail()).isEqualTo("(lamp) Brass or oil?");
    }

    @Test
    void acknowledgesADeliveryWhenTheSameSessionWaitsAgain() {
        UUID primary = primarySession();
        home.recordStatus().report(worker.id(), StatusState.BLOCKED, null, null, "The lamp is missing.");
        home.waitForWakes().deliverTo(primary);

        assertThat(home.waitForWakes().deliverTo(primary)).isEmpty();
        assertThat(home.wakes().unacknowledged()).isEmpty();
    }

    @Test
    void deliversAgainToANewPrimaryWhatTheLastOneNeverAcknowledged() {
        UUID crashed = primarySession();
        home.recordStatus().report(worker.id(), StatusState.FAILED, null, null, "The lamp broke.");
        home.waitForWakes().deliverTo(crashed);

        home.primaryHooks().sessionEnded(crashed);
        UUID next = primarySession();
        WakeDelivery redelivered = home.waitForWakes().deliverTo(next).orElseThrow();

        assertThat(redelivered.wakes()).extracting(QueuedWake::deliveredTo).containsExactly(crashed);
        assertThat(home.wakes().unacknowledged()).extracting(QueuedWake::deliveredTo).containsExactly(next);
    }

    @Test
    void servesOnlyThePrimarySession() {
        UUID primary = primarySession();
        UUID standby = UUID.randomUUID();
        home.primaryHooks().sessionStarted(standby, null);

        home.waitForWakes().requireThePrimary(primary);
        assertThatThrownBy(() -> home.waitForWakes().requireThePrimary(standby)).isInstanceOf(Refusal.class).hasMessageContaining("serves the primary session");
        assertThatThrownBy(() -> home.waitForWakes().requireThePrimary(null)).isInstanceOf(Refusal.class);
    }

    private UUID primarySession() {
        UUID session = UUID.randomUUID();
        home.primaryHooks().sessionStarted(session, null);
        home.primaryHooks().promptSubmitted(session, "Carry on.");
        return session;
    }
}
