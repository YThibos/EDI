package io.github.ythibos.edi.application.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.decision.GivenAnswer;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.authority.Citation;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.primary.SessionSource;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.StageReached;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskStage;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.Cards;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class StoredStageTest {

    private static final TaskId LAMP = new TaskId("fit-lamp");
    private static final TaskId CHOICE = new TaskId("lamp-choice");

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void createTheHome() throws IOException {
        home = WorkerFixtures.homeWithProjects(directory);
    }

    @Test
    void movesAQueuedTaskToWorkingWhenItsWorkerIsLaunched() {
        DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));

        assertThat(home.tasks().get(LAMP).stage()).isEqualTo(TaskStage.WORKING);
        assertThat(stagesOf(LAMP)).extracting(StageReached::stage, StageReached::reason).containsExactly(tuple(TaskStage.WORKING, "dispatched"));
        assertThat(home.eventTypesOf(LAMP)).containsSubsequence("TaskDispatched", "StageChanged");
    }

    @Test
    void deliversOnTheDoneChangesWhenTheSteeredWorkerWorksAgainAndDeliversOnItsNextDone() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        Worker worker = DeliveryFixtures.deliveredAsADraft(home, dispatched);
        TaskStage delivered = home.tasks().get(LAMP).stage();

        home.steerWorker().steer(TaskReference.parse("fit-lamp"), "Fit the brass cap as well.", null, Actor.PRIMARY);
        home.workerInbox().acknowledge(worker.id(), 1);
        report(worker, StatusState.WORKING, "Fitting the cap.");
        TaskStage changing = home.tasks().get(LAMP).stage();
        report(worker, StatusState.DONE, "Fitted, with its cap.");

        assertThat(List.of(delivered, changing)).containsExactly(TaskStage.DELIVERED, TaskStage.CHANGING);
        assertThat(stagesOf(LAMP)).extracting(StageReached::stage, StageReached::reason).containsExactly(
                tuple(TaskStage.WORKING, "dispatched"),
                tuple(TaskStage.DELIVERED, "its worker reported done"),
                tuple(TaskStage.CHANGING, "its worker reported working"),
                tuple(TaskStage.DELIVERED, "its worker reported done"));
        assertThat(home.jdbc().sql("select actor from event where type = 'StageChanged' and task_id = 'fit-lamp' order by sequence")
                .query(String.class).list()).containsExactly("primary", "worker:fit-lamp#1", "worker:fit-lamp#1", "worker:fit-lamp#1");
    }

    @Test
    void keepsADeliveredTaskWhereItIsWhenItsWorkerAsksOrReportsDoneAgain() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        Worker worker = DeliveryFixtures.deliveredAsADraft(home, dispatched);

        report(worker, StatusState.DONE, "Still fitted.");
        home.recordStatus().report(worker.id(), StatusState.NEEDS_DECISION, new StatusKey("which-cap"), null, "Brass or tin cap?");

        assertThat(home.tasks().get(LAMP).stage()).isEqualTo(TaskStage.DELIVERED);
        assertThat(stagesOf(LAMP)).extracting(StageReached::stage).containsExactly(TaskStage.WORKING, TaskStage.DELIVERED);
    }

    @Test
    void retiresACaptainItemOnTheAnswerToItsDecision() {
        home.storing(List.of(WorkerFixtures.queued(CHOICE.value(), TaskKind.CAPTAIN, "harness", null)));
        home.raiseDecision().raise(TaskReference.parse(CHOICE.value()), Cards.card("Which lamp?"), Actor.PRIMARY);

        home.answerDecision().answer(TaskReference.parse(CHOICE.value()), new GivenAnswer("A", new OptionKey('A'), "", AnswerChannel.CHAT),
                Actor.PRIMARY);

        assertThat(home.tasks().get(CHOICE).stage()).isEqualTo(TaskStage.RETIRED);
        assertThat(stagesOf(CHOICE)).extracting(StageReached::stage, StageReached::reason)
                .containsExactly(tuple(TaskStage.RETIRED, "its decision is answered"));
        assertThat(home.eventTypesOf(CHOICE)).endsWith("DecisionAnswered", "StageChanged", "TaskDone");
    }

    @Test
    void dropsAQueuedTaskOnTheCaptainsWord() {
        home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));
        CallingSession session = session("Drop the lamp.");
        home.captainsWord().record(session, new Citation.Verbatim("Drop the lamp."), TaskReference.parse("fit-lamp"), null, Actor.PRIMARY);

        home.dropTask().drop(TaskReference.parse("fit-lamp"), false, session, Actor.PRIMARY);

        assertThat(home.tasks().get(LAMP).stage()).isEqualTo(TaskStage.DROPPED);
        assertThat(stagesOf(LAMP)).extracting(StageReached::stage, StageReached::reason)
                .containsExactly(tuple(TaskStage.DROPPED, "dropped on the captain's word"));
    }

    private List<StageReached> stagesOf(TaskId task) {
        return home.events().stagesOf(task);
    }

    private void report(Worker worker, StatusState state, String text) {
        home.recordStatus().report(worker.id(), state, null, null, text);
    }

    private CallingSession session(String typed) {
        UUID session = UUID.randomUUID();
        home.primaryHooks().sessionStarted(session, null, SessionSource.LAUNCH);
        home.primaryHooks().promptSubmitted(session, typed);
        return new CallingSession(session);
    }
}
