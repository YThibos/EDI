package io.github.ythibos.edi.application.decision;

import static io.github.ythibos.edi.testing.Cards.card;
import static io.github.ythibos.edi.testing.Intakes.ship;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.decision.UnknownDecision;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.RecordedEvent;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskState;
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
    void holdsTheTaskWhileItsDecisionIsOpenAndReleasesItToItsStateOnTheAnswer() {
        home.holdTask().hold(FIT_LAMP, HoldKind.DEFERRAL, "after the storm", HoldUntil.day(LocalDate.of(2026, 11, 1)), Actor.PRIMARY);

        DecisionNumber number = home.raiseDecision().raise(FIT_LAMP, card("Which ladder?"), Actor.PRIMARY);
        AnsweredDecision answered = home.answerDecision().answer(FIT_LAMP, new GivenAnswer("A", new OptionKey('A'), "", AnswerChannel.CHAT), Actor.PRIMARY);

        assertThat(number).isEqualTo(new DecisionNumber(1));
        assertThat(answered.task().state()).isEqualTo(TaskState.QUEUED);
        assertThat(answered.task().isHeld()).isFalse();
        assertThat(home.events().timelineOf(new TaskId("fit-lamp"))).extracting(RecordedEvent::type)
                .containsExactly("TaskAdded", "TaskHeld", "DecisionRaised", "DecisionAnswered");
    }

    @Test
    void answersExactlyTheDecisionItsNumberNamesWhileALaterOneWaitsOnTheSameTask() {
        DecisionNumber earlier = home.raiseDecision().raise(FIT_LAMP, card("Which ladder?"), Actor.PRIMARY);
        home.answerDecision().answer(FIT_LAMP, new GivenAnswer("A", new OptionKey('A'), "", AnswerChannel.CHAT), Actor.PRIMARY);
        DecisionNumber later = home.raiseDecision().raise(FIT_LAMP, card("Which bulb?"), Actor.PRIMARY);
        GivenAnswer again = new GivenAnswer(earlier + ", b", new OptionKey('B'), "", AnswerChannel.CHAT);

        assertThatThrownBy(() -> home.answerDecision().answer(new TaskReference.ByDecision(earlier), again, Actor.PRIMARY))
                .isInstanceOf(Refusal.class)
                .hasMessage(earlier + " is already answered: A");
        assertThat(home.decisions().get(later).isOpen()).isTrue();
    }

    @Test
    void raisesByTheNumberOfAnAnsweredDecisionANewOneAndLeavesTheLaterOneAsItIs() {
        DecisionNumber earlier = home.raiseDecision().raise(FIT_LAMP, card("Which ladder?"), Actor.PRIMARY);
        home.answerDecision().answer(FIT_LAMP, new GivenAnswer("A", new OptionKey('A'), "", AnswerChannel.CHAT), Actor.PRIMARY);
        DecisionNumber later = home.raiseDecision().raise(FIT_LAMP, card("Which bulb?"), Actor.PRIMARY);

        DecisionNumber raised = home.raiseDecision().raise(new TaskReference.ByDecision(earlier), card("Which ladder now?"), Actor.PRIMARY);
        DecisionNumber revised = home.raiseDecision().raise(new TaskReference.ByDecision(raised), card("Which ladder, finally?"), Actor.PRIMARY);

        assertThat(raised).isNotIn(earlier, later);
        assertThat(revised).isEqualTo(raised);
        assertThat(home.decisions().get(raised).card().title()).isEqualTo("Which ladder, finally?");
        assertThat(home.decisions().get(later).card().title()).isEqualTo("Which bulb?");
        assertThat(home.decisions().get(earlier).answer().verbatim()).isEqualTo("A");
    }

    @Test
    void refusesAnAnswerForATaskWithoutADecision() {
        GivenAnswer answer = new GivenAnswer("A", null, "", AnswerChannel.CHAT);

        assertThatThrownBy(() -> home.answerDecision().answer(FIT_LAMP, answer, Actor.PRIMARY))
                .isInstanceOf(UnknownDecision.class)
                .hasMessage("fit-lamp has no decision.");
    }
}
