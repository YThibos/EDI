package io.github.ythibos.edi.application.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.PaneState;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerActivity;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class SteeringTest {

    private static final TaskId TASK = new TaskId("fit-lamp");

    @TempDir
    private Path directory;

    private TestHome home;
    private Worker worker;

    @BeforeEach
    void prepareAWorker() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
    }

    @Test
    void typesTheNoticeIntoAnIdlePromptAndTheTypedPromptProvesTheDelivery() {
        home.workerHooks().sessionStarted(worker.id(), worker.session(), null);

        SteerWorker.Steered steered = steer("Use the brass lamp.", null);
        home.workerHooks().promptSubmitted(worker.id(), home.runtime().typedLines().getLast());

        assertThat(steered.delivery()).isEqualTo(SteerDelivery.TYPED_INTO_THE_IDLE_PROMPT);
        assertThat(home.runtime().typedLines()).containsExactly("EDI_OP: message 1 waiting: run edi inbox");
        assertThat(message(1).isReceived()).isTrue();
        assertThat(home.workers().get(worker.id()).orElseThrow().activity()).isEqualTo(WorkerActivity.BUSY);
    }

    @Test
    void deliversToABusyWorkerAtItsNextStopAndBlocksThatStopOnce() {
        home.workerHooks().promptSubmitted(worker.id(), "the brief");

        SteerWorker.Steered steered = steer("Use the brass lamp.", null);
        HookAnswer firstStop = home.workerHooks().stopping(worker.id());
        HookAnswer secondStop = home.workerHooks().stopping(worker.id());

        assertThat(steered.delivery()).isEqualTo(SteerDelivery.AT_THE_NEXT_STOP);
        assertThat(firstStop.verdict()).isEqualTo(HookAnswer.Verdict.BLOCK_THE_STOP);
        assertThat(firstStop.reason()).contains("run edi inbox");
        assertThat(secondStop).isEqualTo(HookAnswer.PROCEED);
        assertThat(home.runtime().typedLines()).isEmpty();
    }

    @Test
    void keepsAWorkerBusyWhileItsBlockedStopSendsItToTheInbox() {
        home.workerHooks().promptSubmitted(worker.id(), "the brief");
        steer("Use the brass lamp.", null);
        home.workerHooks().stopping(worker.id());

        SteerWorker.Steered second = steer("Then fit the horn.", null);

        assertThat(home.workers().get(worker.id()).orElseThrow().activity()).isEqualTo(WorkerActivity.BUSY);
        assertThat(second.delivery()).isEqualTo(SteerDelivery.AT_THE_NEXT_STOP);
        assertThat(home.runtime().typedLines()).isEmpty();
    }

    @Test
    void typesNothingIntoAPaneThatShowsADialog() {
        home.workerHooks().stopping(worker.id());
        home.runtime().showing(PaneState.DIALOG_SPEND_CAP);

        assertThat(steer("Use the brass lamp.", null).delivery()).isEqualTo(SteerDelivery.WAITING_FOR_THE_PANE);
        assertThat(home.runtime().typedLines()).isEmpty();
    }

    @Test
    void readsAndAcknowledgesTheInboxAndRecordsTheResolvedKey() {
        steer("Option B: use the brass lamp.", new StatusKey("lamp-choice"));
        steer("Then fit the horn.", null);

        List<InboxMessage> read = home.workerInbox().read(worker.id());
        home.workerInbox().acknowledge(worker.id(), 1);
        home.clock().advance(Duration.ofMinutes(10));

        assertThat(read).extracting(InboxMessage::number).containsExactly(1, 2);
        assertThat(read).allMatch(InboxMessage::isReceived);
        assertThat(home.workerInbox().read(worker.id())).extracting(InboxMessage::number).containsExactly(2);
        assertThat(home.noticeOverdueSteers().notice()).isEmpty();
        assertThatThrownBy(() -> home.workerInbox().acknowledge(worker.id(), 1)).isInstanceOf(Refusal.class).hasMessageContaining("already acknowledged");
        assertThat(home.eventTypesOf(TASK)).containsSubsequence("SteerSent", "StatusReported", "SteerSent", "SteerReceived", "SteerReceived", "SteerAcknowledged");
    }

    @Test
    void waitsInTheInboxWhenTheWorkersWindowIsClosed() {
        home.runtime().closeEveryWindow();

        assertThat(steer("Use the brass lamp.", null).delivery()).isEqualTo(SteerDelivery.WAITING_FOR_A_LIVE_WORKER);
        assertThat(message(1).isReceived()).isFalse();
    }

    private SteerWorker.Steered steer(String text, StatusKey resolves) {
        return home.steerWorker().steer(TaskReference.parse(TASK.value()), text, resolves, Actor.PRIMARY);
    }

    private InboxMessage message(int number) {
        return home.inbox().get(TASK, number).orElseThrow();
    }
}
