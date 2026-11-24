package io.github.ythibos.edi.application.decision;

import static io.github.ythibos.edi.testing.Cards.card;
import static io.github.ythibos.edi.testing.Intakes.ship;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.decision.UnknownDecision;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.RecordedEvent;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.testing.Cards;
import io.github.ythibos.edi.testing.Intakes;
import io.github.ythibos.edi.testing.TestHome;

@Tag("adapter")
class RaiseAndAnswerDecisionTest {

    private static final TaskReference FIT_LAMP = new TaskReference.ById(new TaskId("fit-lamp"));

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void queueAShip() throws IOException {
        home = TestHome.in(directory).registering(Intakes.REGISTRY);
        home.addTask().add(ship("fit-lamp", "Fit the lamp"), Actor.PRIMARY);
    }

    @Test
    void keepsADeferredTaskDeferredAfterTheAnswerToADecisionRaisedOnIt() {
        home.holdTask().hold(FIT_LAMP, HoldKind.DEFERRAL, "after the storm", HoldUntil.day(LocalDate.of(2026, 11, 1)), Actor.PRIMARY);

        DecisionNumber number = home.raiseDecision().raise(FIT_LAMP, card("Which ladder?"), Actor.PRIMARY);
        Task waiting = home.tasks().get(new TaskId("fit-lamp"));
        AnsweredDecision answered = home.answerDecision().answer(FIT_LAMP, new GivenAnswer("A", new OptionKey('A'), "", AnswerChannel.CHAT), Actor.PRIMARY);

        assertThat(number).isEqualTo(new DecisionNumber(1));
        assertThat(waiting.reasonsItWaits()).isEqualTo("deferred: after the storm; D1 waits for its answer");
        assertThat(answered.task().state()).isEqualTo(TaskState.QUEUED);
        assertThat(answered.task().reasonsItWaits()).isEqualTo("deferred: after the storm");
        assertThat(home.tasks().get(new TaskId("fit-lamp")).reasonsItWaits()).isEqualTo("deferred: after the storm");
        assertThat(home.events().timelineOf(new TaskId("fit-lamp"))).extracting(RecordedEvent::type)
                .containsExactly("TaskAdded", "TaskHeld", "DecisionRaised", "DecisionAnswered");
    }

    @Test
    void raisesASecondQuestionAsASecondOpenDecisionAndAnswersEachOnItsOwn() {
        DecisionNumber ladder = home.raiseDecision().raise(FIT_LAMP, card("Which ladder?"), Actor.PRIMARY);
        DecisionNumber bulb = home.raiseDecision().raise(FIT_LAMP, card("Which bulb?"), Actor.PRIMARY);

        AnsweredDecision answered = home.answerDecision().answer(new TaskReference.ByDecision(ladder),
                new GivenAnswer("A", new OptionKey('A'), "", AnswerChannel.CHAT), Actor.PRIMARY);

        assertThat(List.of(ladder, bulb)).containsExactly(new DecisionNumber(1), new DecisionNumber(2));
        assertThat(home.decisions().get(ladder).card().title()).isEqualTo("Which ladder?");
        assertThat(answered.task().holds().openDecisions()).containsExactly(bulb);
        assertThat(home.tasks().get(new TaskId("fit-lamp")).holds().openDecisions()).containsExactly(bulb);
        assertThat(home.decisions().open()).extracting(Decision::number).containsExactly(bulb);
    }

    @Test
    void asksForTheNumberWhenTheTaskWaitsOnTwoDecisions() {
        home.raiseDecision().raise(FIT_LAMP, card("Which ladder?"), Actor.PRIMARY);
        home.raiseDecision().raise(FIT_LAMP, card("Which bulb?"), Actor.PRIMARY);
        GivenAnswer answer = new GivenAnswer("A", new OptionKey('A'), "", AnswerChannel.CHAT);

        assertThatThrownBy(() -> home.answerDecision().answer(FIT_LAMP, answer, Actor.PRIMARY))
                .isInstanceOf(Refusal.class)
                .hasMessage("fit-lamp waits on D1 and D2; name one of them by its number.");
        assertThat(home.decisions().open()).extracting(Decision::number).containsExactly(new DecisionNumber(1), new DecisionNumber(2));
    }

    @Test
    void asksForTheNumberOfTheDecisionToRecheckWhenTheTaskWaitsOnTwo() {
        home.raiseDecision().raise(FIT_LAMP, card("Which ladder?"), Actor.PRIMARY);
        DecisionNumber bulb = home.raiseDecision().raise(FIT_LAMP, card("Which bulb?"), Actor.PRIMARY);

        assertThatThrownBy(() -> home.askForARecheck().ask(FIT_LAMP, "Is it still open?", Actor.PRIMARY))
                .isInstanceOf(Refusal.class)
                .hasMessage("fit-lamp waits on D1 and D2; name one of them by its number.");
        assertThat(home.askForARecheck().ask(new TaskReference.ByDecision(bulb), "Is it still open?", Actor.PRIMARY).number()).isEqualTo(bulb);
    }

    @Test
    void raisesTheSameQuestionAgainUnderItsOpenNumber() {
        DecisionNumber first = home.raiseDecision().raise(FIT_LAMP, card("Which ladder?"), Actor.PRIMARY);
        home.raiseDecision().raise(FIT_LAMP, card("Which bulb?"), Actor.PRIMARY);

        DecisionNumber again = home.raiseDecision().raise(FIT_LAMP, Cards.urgent("Which ladder?"), Actor.PRIMARY);

        assertThat(again).isEqualTo(first);
        assertThat(home.decisions().get(first).card().urgent()).isTrue();
        assertThat(home.tasks().get(new TaskId("fit-lamp")).holds().openDecisions()).containsExactly(new DecisionNumber(1), new DecisionNumber(2));
    }

    @Test
    void answersExactlyTheDecisionItsNumberNamesWhileALaterOneWaitsOnTheSameTask() {
        DecisionNumber earlier = home.raiseDecision().raise(FIT_LAMP, card("Which ladder?"), Actor.PRIMARY);
        home.answerDecision().answer(FIT_LAMP, new GivenAnswer("A", new OptionKey('A'), "", AnswerChannel.CHAT), Actor.PRIMARY);
        DecisionNumber later = home.raiseDecision().raise(FIT_LAMP, card("Which bulb?"), Actor.PRIMARY);
        GivenAnswer again = new GivenAnswer(earlier + ", b", new OptionKey('B'), "", AnswerChannel.CHAT);

        assertThatThrownBy(() -> home.answerDecision().answer(new TaskReference.ByDecision(earlier), again, Actor.PRIMARY))
                .isInstanceOf(Refusal.class)
                .hasMessage(earlier + " is already answered: \"A\"");
        assertThat(home.decisions().get(later).isOpen()).isTrue();
    }

    @Test
    void answersTheOneOpenDecisionOfATaskByItsIdAfterALaterOneWasAnswered() {
        DecisionNumber ladder = home.raiseDecision().raise(FIT_LAMP, card("Which ladder?"), Actor.PRIMARY);
        DecisionNumber bulb = home.raiseDecision().raise(FIT_LAMP, card("Which bulb?"), Actor.PRIMARY);
        home.answerDecision().answer(new TaskReference.ByDecision(bulb), new GivenAnswer("A", new OptionKey('A'), "", AnswerChannel.CHAT), Actor.PRIMARY);

        AnsweredDecision answered = home.answerDecision().answer(FIT_LAMP, new GivenAnswer("B", new OptionKey('B'), "", AnswerChannel.CHAT), Actor.PRIMARY);

        assertThat(answered.decision().number()).isEqualTo(ladder);
        assertThat(answered.task().isHeld()).isFalse();
    }

    @Test
    void raisesByTheNumberOfAnAnsweredDecisionANewDecisionUnlessItsTaskAlreadyAsksTheSame() {
        DecisionNumber earlier = home.raiseDecision().raise(FIT_LAMP, card("Which ladder?"), Actor.PRIMARY);
        home.answerDecision().answer(FIT_LAMP, new GivenAnswer("A", new OptionKey('A'), "", AnswerChannel.CHAT), Actor.PRIMARY);
        DecisionNumber later = home.raiseDecision().raise(FIT_LAMP, card("Which bulb?"), Actor.PRIMARY);

        DecisionNumber sameQuestion = home.raiseDecision().raise(new TaskReference.ByDecision(earlier), Cards.urgent("Which bulb?"), Actor.PRIMARY);
        DecisionNumber newQuestion = home.raiseDecision().raise(new TaskReference.ByDecision(earlier), card("Which ladder now?"), Actor.PRIMARY);

        assertThat(sameQuestion).isEqualTo(later);
        assertThat(newQuestion).isEqualTo(new DecisionNumber(3));
        assertThat(home.decisions().get(earlier).answer().verbatim()).isEqualTo("A");
        assertThat(home.decisions().open()).extracting(Decision::number).containsExactly(later, newQuestion);
    }

    @Test
    void refusesAnAnswerForATaskWithoutADecision() {
        GivenAnswer answer = new GivenAnswer("A", null, "", AnswerChannel.CHAT);

        assertThatThrownBy(() -> home.answerDecision().answer(FIT_LAMP, answer, Actor.PRIMARY))
                .isInstanceOf(UnknownDecision.class)
                .hasMessage("fit-lamp has no decision.");
    }
}
