package io.github.ythibos.edi.application.decision;

import static io.github.ythibos.edi.testing.Cards.card;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.worker.SteerWorker.Steered;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class AnswersReachTheirWorkerTest {

    private static final TaskId LAMP = new TaskId("fit-lamp");
    private static final TaskReference FIT_LAMP = new TaskReference.ById(LAMP);
    private static final StatusKey LAMP_CHOICE = new StatusKey("lamp-choice");
    private static final StatusKey REVIEW_GATE = new StatusKey("nm-4-review");

    @TempDir
    private Path directory;

    private TestHome home;
    private Worker worker;

    @BeforeEach
    void serveAWorker() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip(LAMP.value(), "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
    }

    @Test
    void carriesTheAnswerToTheWorkerWaitingOnItsQuestionAsASteerResolvingItsKey() {
        home.recordStatus().report(worker.id(), StatusState.NEEDS_DECISION, LAMP_CHOICE, null, "Brass or oil?");
        home.raiseDecision().raise(FIT_LAMP, card("Brass or oil?"), Actor.PRIMARY);

        AnsweredDecision answered = home.answerDecision().answer(FIT_LAMP,
                new GivenAnswer("B, oil it is", new OptionKey('B'), "Fit the oil lamp.", AnswerChannel.CONSOLE), Actor.CAPTAIN);
        InboxMessage carried = home.inbox().get(LAMP, 1).orElseThrow();

        assertThat(carried.resolves()).isEqualTo(LAMP_CHOICE);
        assertThat(carried.body()).isEqualTo("""
                Answer to D1 Brass or oil?
                Chosen: option B, The thorough way
                The captain's words: "B, oil it is"
                Understood as: Fit the oil lamp.""");
        assertThat(answered.carriedToItsWorker()).map(Steered::message).contains(carried);
        assertThat(home.eventTypesOf(LAMP)).containsSubsequence("DecisionAnswered", "SteerSent", "StatusReported");
        assertThat(home.undeliveredAnswers().waiting()).isEmpty();

        home.workerInbox().acknowledge(worker.id(), 1);

        assertThat(home.workers().get(worker.id()).orElseThrow().waitsOnThePrimary()).isFalse();
    }

    @Test
    void sendsNothingWhenTheTasksWorkerWaitsOnNoQuestion() {
        home.recordStatus().report(worker.id(), StatusState.WORKING, null, null, "Fitting the bracket.");
        home.raiseDecision().raise(FIT_LAMP, card("Brass or oil?"), Actor.PRIMARY);

        AnsweredDecision answered = home.answerDecision().answer(FIT_LAMP, new GivenAnswer("A", new OptionKey('A'), "", AnswerChannel.CHAT), Actor.PRIMARY);

        assertThat(answered.carriedToItsWorker()).isEmpty();
        assertThat(home.inbox().allOf(LAMP)).isEmpty();
    }

    @Test
    void sendsNothingToAWorkerWhoseDoneWasAccepted() {
        home.workers().update(home.workers().get(worker.id()).orElseThrow().reported(new StatusReport(StatusState.DONE, null, null, "Fitted."), home.clock().now()));
        home.raiseDecision().raise(FIT_LAMP, card("Merge as is or rework?"), Actor.PRIMARY);

        AnsweredDecision answered = home.answerDecision().answer(FIT_LAMP,
                new GivenAnswer("A, merge as is", new OptionKey('A'), "", AnswerChannel.CONSOLE), Actor.CAPTAIN);

        assertThat(home.workers().get(worker.id()).orElseThrow().hadItsDoneAccepted()).isTrue();
        assertThat(answered.carriedToItsWorker()).isEmpty();
        assertThat(home.inbox().unacknowledgedOf(LAMP)).isEmpty();
    }

    @Test
    void leavesAQuestionAskedAfterTheDecisionWasRaisedOpenWhileTheAnswerStillReachesTheWorkerWaitingOnIt() {
        home.recordStatus().report(worker.id(), StatusState.NEEDS_DECISION, LAMP_CHOICE, null, "Brass or oil?");
        home.raiseDecision().raise(FIT_LAMP, card("Brass or oil?"), Actor.PRIMARY);
        home.clock().advance(Duration.ofMinutes(5));
        home.recordStatus().report(worker.id(), StatusState.BLOCKED, REVIEW_GATE, null, "The review asks about the wiring.");
        home.clock().advance(Duration.ofMinutes(5));

        AnsweredDecision answered = home.answerDecision().answer(FIT_LAMP,
                new GivenAnswer("B, oil it is", new OptionKey('B'), "", AnswerChannel.CONSOLE), Actor.CAPTAIN);
        InboxMessage carried = home.inbox().get(LAMP, 1).orElseThrow();
        home.workerInbox().acknowledge(worker.id(), 1);
        Worker acknowledged = home.workers().get(worker.id()).orElseThrow();

        assertThat(answered.carriedToItsWorker()).map(Steered::message).contains(carried);
        assertThat(carried.resolves()).isNull();
        assertThat(carried.body()).startsWith("Answer to D1 Brass or oil?");
        assertThat(acknowledged.waitsOnThePrimary()).isTrue();
        assertThat(acknowledged.lastStatus().report().key()).isEqualTo(REVIEW_GATE);
        assertThat(home.undeliveredAnswers().waiting()).isEmpty();
    }

    @Test
    void sendsNothingWhenTheTaskHasNoServingWorker() {
        home.storing(List.of(WorkerFixtures.queuedShip("hang-bell", "harness", "HRN-4")));
        TaskReference unshipped = TaskReference.parse("hang-bell");
        home.raiseDecision().raise(unshipped, card("Bronze or iron?"), Actor.PRIMARY);

        AnsweredDecision answered = home.answerDecision().answer(unshipped, new GivenAnswer("A", new OptionKey('A'), "", AnswerChannel.CHAT), Actor.PRIMARY);

        assertThat(answered.carriedToItsWorker()).isEmpty();
    }

    @Test
    void settlesAQuestionWithoutACardWithOneSteerThatResolvesItsKey() {
        home.recordStatus().report(worker.id(), StatusState.BLOCKED, LAMP_CHOICE, null, "The bracket does not fit.");

        home.steerWorker().steer(FIT_LAMP, "Use the long bracket.", LAMP_CHOICE, Actor.PRIMARY);
        home.workerInbox().acknowledge(worker.id(), 1);
        Worker settled = home.workers().get(worker.id()).orElseThrow();

        assertThat(settled.waitsOnThePrimary()).isFalse();
        assertThat(settled.lastStatus().report().state()).isEqualTo(StatusState.RESOLVED);
        assertThat(settled.lastStatus().report().key()).isEqualTo(LAMP_CHOICE);
        assertThat(home.inbox().get(LAMP, 1).orElseThrow().body()).isEqualTo("Use the long bracket.");
    }
}
