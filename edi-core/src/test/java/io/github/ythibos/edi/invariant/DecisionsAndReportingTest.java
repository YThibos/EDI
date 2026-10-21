package io.github.ythibos.edi.invariant;

import static io.github.ythibos.edi.testing.Cards.card;
import static io.github.ythibos.edi.testing.Intakes.captainItem;
import static io.github.ythibos.edi.testing.Intakes.ship;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.decision.GivenAnswer;
import io.github.ythibos.edi.application.worker.HookAnswer;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.domain.voice.CaptainVoice;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.testing.Intakes;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.web.report.CaptainReport;
import io.github.ythibos.edi.web.report.CaptainReportPresenter;
import io.github.ythibos.edi.web.report.ReportWidth;
import io.github.ythibos.edi.web.report.TaskWording;
import io.github.ythibos.edi.web.report.Turn;
import io.github.ythibos.edi.web.report.TurnItem;

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

            String report = captainReport(home, aTurnAboutOtherWork());
            assertThat(report).contains("║  " + open + "   Which lamp?", "║        (A)  The simple way", "║        » Recommended: A - it is enough");
        }

        private static Turn aTurnAboutOtherWork() {
            return new Turn(
                    List.of(new TurnItem(List.of("harness"), "Fitted the lamp bracket")),
                    List.of(new TurnItem(List.of(), "Choose the paint colour")),
                    Map.of("lamp-fitting", new TaskWording("Fit the brass lamp", "waiting for the ladder", null)));
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
            assertThat(captainReport(home, Turn.NONE)).doesNotContain(number.toString(), "Which lamp?").contains("nothing open");
        }

        @Test
        @Tag("adapter")
        @DisplayName("AT-20 an answer given in the EDI console lands verbatim, as the captain's own word, and wakes the primary")
        void anAnswerGivenInTheConsoleLandsVerbatim() throws IOException {
            TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY).continuingDecisionsFrom(MIGRATED_DECISION_SEQUENCE);
            home.addTask().add(captainItem("lamp-choice", "Choose the lamp"), Actor.PRIMARY);
            DecisionNumber number = raise(home, "lamp-choice", "Which lamp?");

            home.answerDecision().answer(new TaskReference.ByDecision(number), new GivenAnswer(VERBATIM, new OptionKey('A'), "", AnswerChannel.CONSOLE),
                    Actor.CAPTAIN);

            CaptainAnswer recorded = home.decisions().get(number).answer();
            assertThat(recorded.verbatim()).isEqualTo(VERBATIM);
            assertThat(recorded.channel()).isEqualTo(AnswerChannel.CONSOLE);
            assertThat(home.jdbc().sql("select actor from event where type = 'DecisionAnswered'").query(String.class).single()).isEqualTo("captain");
            assertThat(home.wakes().unacknowledged()).extracting(queued -> queued.wake().kind()).containsExactly(WakeKind.DECISION_ANSWERED);
            assertThat(captainReport(home, Turn.NONE)).doesNotContain(number.toString()).contains("nothing open");
        }
    }

    @Nested
    @DisplayName("I-21 Every state-changing turn ends with the boxed captain report")
    class StateChangingTurnsEndWithTheReport {

        @Test
        @DisplayName("AT-21 the primary's Stop hook blocks a state-changing turn until edi report ran in that turn")
        void theStopHookWaitsForTheReport() throws IOException {
            TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY);
            UUID primary = UUID.randomUUID();
            home.primaryHooks().sessionStarted(primary, null, true);
            home.primaryHooks().promptSubmitted(primary, "What is waiting on me?");
            HookAnswer quietTurn = home.primaryHooks().stopping(primary, List.of());

            home.primaryHooks().promptSubmitted(primary, "Queue the brass lamp.");
            home.addTask().add(ship("fit-lamp", "Fit the brass lamp"), Actor.PRIMARY);
            HookAnswer unreported = home.primaryHooks().stopping(primary, List.of());
            home.primaryHooks().reportRendered(primary);
            HookAnswer reported = home.primaryHooks().stopping(primary, List.of());

            assertThat(quietTurn).isEqualTo(HookAnswer.PROCEED);
            assertThat(unreported.verdict()).isEqualTo(HookAnswer.Verdict.BLOCK_THE_STOP);
            assertThat(unreported.reason()).isEqualTo("This turn changed EDI's state: render the captain report with edi report and paste it verbatim, then stop.");
            assertThat(reported).isEqualTo(HookAnswer.PROCEED);
        }
    }

    @Nested
    @DisplayName("I-22 Talk in outcomes")
    class TalkInOutcomes {

        @Test
        @Tag("adapter")
        @DisplayName("AT-22 a lint over captain-facing strings rejects the internal-label list")
        void captainFacingStringsCarryNoInternalLabels() throws IOException {
            List<String> generated = CaptainFacingStrings.everyKindEdiGenerates(directory);

            assertThat(CaptainVoice.INTERNAL_LABELS).allSatisfy(label -> assertThat(CaptainVoice.internalLabelsIn("The " + label.label() + " is ready."))
                    .as(label.label()).contains(label));
            assertThat(CaptainVoice.internalLabelsIn("The `worktree` at /home/example/worktrees/lamp, \"Fix the worktree\" and https://example.org/wake"))
                    .isEmpty();
            assertThatThrownBy(() -> CaptainVoice.requireOutcomes("A decision card", "Discard the worktree of the lamp?"))
                    .isInstanceOf(Refusal.class)
                    .hasMessage("A decision card speaks to the captain in outcomes: say \"isolated copy\" instead of \"worktree\", or quote a name in"
                            + " double quotes when it is one (I-22).");
            assertThat(generated).hasSizeGreaterThan(40)
                    .allSatisfy(text -> assertThat(CaptainVoice.internalLabelsIn(text)).as(text).isEmpty());
        }
    }

    private static String captainReport(TestHome home, Turn turn) {
        return new CaptainReportPresenter().text(new CaptainReport(home.fleetSnapshot().snapshot(), turn, ReportWidth.standard(), Presence.presentSince(TestHome.START)));
    }

    private static DecisionNumber raise(TestHome home, String task, String title) {
        return home.raiseDecision().raise(new TaskReference.ById(new TaskId(task)), card(title), Actor.PRIMARY);
    }

    private static void answer(TestHome home, DecisionNumber number) {
        home.answerDecision().answer(new TaskReference.ByDecision(number), new GivenAnswer(number + " A", new OptionKey('A'), "", AnswerChannel.CHAT), Actor.PRIMARY);
    }
}
