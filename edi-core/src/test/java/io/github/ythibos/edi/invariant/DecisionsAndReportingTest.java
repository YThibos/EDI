package io.github.ythibos.edi.invariant;

import static io.github.ythibos.edi.testing.Cards.card;
import static io.github.ythibos.edi.testing.Intakes.captainItem;
import static io.github.ythibos.edi.testing.Intakes.ship;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.decision.GivenAnswer;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.testing.Intakes;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.web.decision.DecisionListText;

@DisplayName("4.4 Decisions and reporting")
class DecisionsAndReportingTest {

    private static final int MIGRATED_DECISION_SEQUENCE = 57;

    @TempDir
    private Path directory;

    @Nested
    @DisplayName("I-18 Decisions are durable and numbered")
    class DecisionsAreDurableAndNumbered {

        @Test
        @Tag("adapter")
        @DisplayName("AT-18 assigning, re-holding, closing, archiving and assigning again yields strictly increasing D numbers from the migrated sequence")
        void decisionNumbersStrictlyIncrease() throws IOException {
            TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY).continuingDecisionsFrom(MIGRATED_DECISION_SEQUENCE);
            home.addTask().add(captainItem("lamp-choice", "Choose the lamp"), Actor.PRIMARY);
            home.addTask().add(ship("lamp-fitting", "Fit the lamp"), Actor.PRIMARY);
            home.addTask().add(captainItem("paint-choice", "Choose the paint"), Actor.PRIMARY);

            DecisionNumber assigned = raise(home, "lamp-choice", "Which lamp?");
            DecisionNumber reheld = raise(home, "lamp-choice", "Which lamp, now that oil is scarce?");
            answer(home, assigned);
            DecisionNumber afterArchiving = raise(home, "lamp-fitting", "Fit it today?");
            assertThatThrownBy(() -> raise(home, "lamp-choice", "Which lamp, once more?")).isInstanceOf(Refusal.class);
            DecisionNumber afterARefusal = raise(home, "paint-choice", "Which paint?");
            answer(home, afterArchiving);
            DecisionNumber reraisedAfterClosing = raise(home, "lamp-fitting", "Fit it tomorrow instead?");

            assertThat(assigned).isEqualTo(new DecisionNumber(58));
            assertThat(reheld).isEqualTo(assigned);
            assertThat(home.tasks().get(new TaskId("lamp-choice")).state()).isEqualTo(TaskState.DONE);
            assertThat(afterArchiving).isEqualTo(new DecisionNumber(59));
            assertThat(afterARefusal).isEqualTo(new DecisionNumber(60));
            assertThat(reraisedAfterClosing).isEqualTo(afterArchiving);
            assertThat(home.decisions().all()).extracting(decision -> decision.number().value()).containsExactly(58, 59, 60);
        }
    }

    @Nested
    @DisplayName("I-19 Every open decision appears in every captain report until answered")
    class OpenDecisionsAlwaysAppear {

        @Test
        @Tag("adapter")
        @DisplayName("AT-19 a decision absent from the turn input still renders in the report")
        void anOpenDecisionRendersWithoutTurnInput() throws IOException {
            TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY).continuingDecisionsFrom(MIGRATED_DECISION_SEQUENCE);
            home.addTask().add(captainItem("lamp-choice", "Choose the lamp"), Actor.PRIMARY);
            DecisionNumber open = raise(home, "lamp-choice", "Which lamp?");

            turnsThatNeverMentionTheDecision(home);

            String report = new DecisionListText().text(home.decisionList().open());
            assertThat(report).contains(open + "  lamp-choice  Which lamp?", "(A) The simple way", "Recommended: A - it is enough");
        }

        private void turnsThatNeverMentionTheDecision(TestHome home) {
            home.addTask().add(ship("lamp-fitting", "Fit the lamp"), Actor.PRIMARY);
            home.clock().advance(Duration.ofHours(1));
            home.noteTask().note(new TaskReference.ById(new TaskId("lamp-fitting")), "The ladder is ready.", Actor.PRIMARY);
            home.clock().advance(Duration.ofDays(1));
            home.addTask().add(captainItem("paint-choice", "Choose the paint"), Actor.PRIMARY);
            raise(home, "paint-choice", "Which paint?");
            answer(home, new DecisionNumber(59));
        }
    }

    @Nested
    @DisplayName("I-20 The captain's answer is recorded verbatim with its interpretation")
    class AnswersAreRecordedVerbatim {

        private static final String VERBATIM = "  D58, A -- but keep the oil lamp\nin the store for a winter. ";

        @Test
        @Tag("adapter")
        @DisplayName("AT-20 answering stores the exact text, the option key and the interpretation, and the decision leaves the report")
        void answeringStoresTheVerbatimAnswer() throws IOException {
            TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY).continuingDecisionsFrom(MIGRATED_DECISION_SEQUENCE);
            home.addTask().add(captainItem("lamp-choice", "Choose the lamp"), Actor.PRIMARY);
            DecisionNumber number = raise(home, "lamp-choice", "Which lamp?");
            home.clock().advance(Duration.ofMinutes(5));

            home.answerDecision().answer(new TaskReference.ByDecision(number),
                    new GivenAnswer(VERBATIM, new OptionKey('A'), "Fit the simple lamp; the oil lamp stays in the store.", AnswerChannel.CHAT),
                    Actor.PRIMARY);

            CaptainAnswer recorded = home.decisions().get(number).answer();
            assertThat(recorded.verbatim()).isEqualTo(VERBATIM);
            assertThat(recorded.option()).isEqualTo(new OptionKey('A'));
            assertThat(recorded.interpretation()).isEqualTo("Fit the simple lamp; the oil lamp stays in the store.");
            assertThat(recorded.answeredAt()).isEqualTo(TestHome.START.plus(Duration.ofMinutes(5)));
            assertThat(home.decisionList().open().decisions()).isEmpty();
            assertThat(new DecisionListText().text(home.decisionList().open())).doesNotContain(number.toString());
        }
    }

    @Nested
    @DisplayName("I-21 Every state-changing turn ends with the boxed captain report")
    class StateChangingTurnsEndWithTheReport {

        @Test
        @DisplayName("AT-21 the primary's Stop hook blocks a state-changing turn until edi report ran in that turn")
        void theStopHookWaitsForTheReport() {
            Pending.until(Slice.S4);
        }
    }

    @Nested
    @DisplayName("I-22 Talk in outcomes")
    class TalkInOutcomes {

        @Test
        @DisplayName("AT-22 a lint over captain-facing strings rejects the internal-label list")
        void captainFacingStringsCarryNoInternalLabels() {
            Pending.until(Slice.S8);
        }
    }

    private static DecisionNumber raise(TestHome home, String task, String title) {
        return home.raiseDecision().raise(new TaskReference.ById(new TaskId(task)), card(title), Actor.PRIMARY);
    }

    private static void answer(TestHome home, DecisionNumber number) {
        home.answerDecision().answer(new TaskReference.ByDecision(number), new GivenAnswer(number + " A", new OptionKey('A'), "", AnswerChannel.CHAT), Actor.PRIMARY);
    }
}
