package io.github.ythibos.edi.invariant;

import static io.github.ythibos.edi.testing.Cards.card;
import static io.github.ythibos.edi.testing.Intakes.captainItem;
import static io.github.ythibos.edi.testing.Intakes.ship;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
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
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.board.BoardFeedback;
import io.github.ythibos.edi.domain.board.BoardPrompt;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.status.CaptainAddress;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.domain.voice.CaptainVoice;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.BoardRounds;
import io.github.ythibos.edi.testing.Intakes;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;
import io.github.ythibos.edi.web.console.ConsoleSitrep;
import io.github.ythibos.edi.web.console.SitrepConsolePresenter;
import io.github.ythibos.edi.web.presence.ReturnBriefText;
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
    @DisplayName("I-19 Everything that waits on the captain reaches the decision zone, and nothing else poses as waiting on him")
    class EverythingThatWaitsOnTheCaptainAppears {

        private static final String REQUEST = "https://github.com/example-owner/harness/pull/7";

        @Test
        @Tag("adapter")
        @DisplayName("AT-19 a decision on a blocked task is counted in the decision zone of the report, the console and the return brief while its task stays parked")
        void aDecisionOnABlockedTaskStaysInTheDecisionZone() throws IOException {
            TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY).continuingDecisionsFrom(MIGRATED_DECISION_SEQUENCE);
            home.addTask().add(ship("refuse-cleanup", "Refuse the cleanup"), Actor.PRIMARY);
            home.addTask().add(Intakes.shipBlockedBy("plan-timesheet", "Plan the timesheet", "refuse-cleanup"), Actor.PRIMARY);

            DecisionNumber blocked = raise(home, "plan-timesheet", "Book on which ticket?");

            assertThat(captainReport(home, Turn.NONE)).contains("╔══ NEEDS YOUR DECISION ══ 1 open ══", "║  " + blocked + "   Book on which ticket?",
                    "PARKED  (1)", "• " + blocked + " Book on which ticket? - blocked by Refuse the cleanup").doesNotContain("nothing open");
            assertThat(consoleSitrep(home).decisions()).extracting(ConsoleSitrep.Card::number).containsExactly(blocked.toString());
            assertThat(returnBrief(home)).contains("Needs your decision (1)\n  " + blocked + "  Book on which ticket?\n");
        }

        @Test
        @Tag("adapter")
        @DisplayName("AT-19 dropping or finishing a task takes it out of every other task's blocked-by, so nothing shows blocked by a dropped task")
        void aClosedTaskBlocksNobody() throws IOException {
            TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY).continuingDecisionsFrom(MIGRATED_DECISION_SEQUENCE);
            home.addTask().add(ship("refuse-cleanup", "Refuse the cleanup"), Actor.PRIMARY);
            home.addTask().add(captainItem("choose-drill", "Choose the drill"), Actor.PRIMARY);
            home.addTask().add(Intakes.shipBlockedBy("plan-timesheet", "Plan the timesheet", "refuse-cleanup", "choose-drill"), Actor.PRIMARY);
            DecisionNumber drill = raise(home, "choose-drill", "Which drill?");
            DecisionNumber timesheet = raise(home, "plan-timesheet", "Book on which ticket?");

            home.dropTask().drop(TaskReference.parse("refuse-cleanup"), false, captainSays(home, "Drop the cleanup drill.", "refuse-cleanup"), Actor.PRIMARY);
            TaskId stillWaitingFor = home.tasks().get(new TaskId("plan-timesheet")).intake().blockedBy().getFirst();
            answer(home, drill);

            assertThat(stillWaitingFor).isEqualTo(new TaskId("choose-drill"));
            assertThat(home.tasks().get(new TaskId("plan-timesheet")).intake().blockedBy()).isEmpty();
            assertThat(captainReport(home, Turn.NONE)).contains("║  " + timesheet + "   Book on which ticket?").doesNotContain("blocked by", "PARKED");
        }

        @Test
        @Tag("adapter")
        @DisplayName("AT-19 a research board with unanswered questions waits in the decision zone with its link and count until a round answers it,"
                + " and again when a new round is posted")
        void aBoardWithQuestionsWaitsOnTheCaptain() throws IOException {
            TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY);
            Worker scout = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queued("survey-lamps", TaskKind.SCOUT, "harness", "HRN-4"), CategoryId.RESEARCH);
            Path board = Files.writeString(Files.createDirectories(home.home().taskDirectory(scout.id().task())).resolve("board.html"), "<!doctype html>\n");
            home.boardHost().holdsTheNextPollOpen();

            Board armed = home.armBoard().arm(scout.id(), TaskReference.parse("survey-lamps"), board, BoardRounds.asking(3));
            String posted = captainReport(home, Turn.NONE);
            String postedBrief = returnBrief(home);
            List<ConsoleSitrep.Board> postedOnTheConsole = consoleSitrep(home).boards();
            home.receiveBoardFeedback().receive(armed.id(), new BoardFeedback.Answered(List.of(typed("Brass, and keep the oil lamp.")), false));
            String answered = captainReport(home, Turn.NONE);
            home.armBoard().arm(scout.id(), TaskReference.parse("survey-lamps"), board, BoardRounds.asking(1));
            String postedAgain = captainReport(home, Turn.NONE);

            String waiting = "║  Board for Work on survey-lamps: 3 questions waiting at " + BoardRounds.LINK + "\n";
            assertThat(posted).contains("NEEDS YOUR DECISION ══ 1 open", waiting);
            assertThat(postedBrief).contains("Needs your decision (1)\n  Board for Work on survey-lamps: 3 questions waiting at " + BoardRounds.LINK + "\n");
            assertThat(postedOnTheConsole).containsExactly(new ConsoleSitrep.Board("survey-lamps", "Work on survey-lamps", List.of("harness"), 3, BoardRounds.LINK));
            assertThat(answered).contains("nothing open").doesNotContain("Board for");
            assertThat(postedAgain).contains("NEEDS YOUR DECISION ══ 1 open", "║  Board for Work on survey-lamps: 1 question waiting at " + BoardRounds.LINK);
        }

        @Test
        @Tag("adapter")
        @DisplayName("AT-19 the primary puts the new round of a board whose scout stopped before the captain, and a round that asks only bound decisions"
                + " leaves them to their cards")
        void thePrimaryPutsAStoppedScoutsBoardBeforeTheCaptain() throws IOException {
            TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY).continuingDecisionsFrom(MIGRATED_DECISION_SEQUENCE);
            Worker scout = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queued("survey-lamps", TaskKind.SCOUT, "harness", "HRN-4"), CategoryId.RESEARCH);
            DecisionNumber lamp = raise(home, "survey-lamps", "Which lamp?");
            Path board = Files.writeString(Files.createDirectories(home.home().taskDirectory(scout.id().task())).resolve("board.html"), "<!doctype html>\n");
            home.boardHost().holdsTheNextPollOpen();

            home.armBoard().arm(scout.id(), TaskReference.parse("survey-lamps"), board, BoardRounds.asking(0, lamp));
            String boundOnly = captainReport(home, Turn.NONE);
            home.workers().update(home.workers().get(scout.id()).orElseThrow().retired(TestHome.START));
            home.postBoardRound().post(board, BoardRounds.LINK, 2, Actor.PRIMARY);
            String posted = captainReport(home, Turn.NONE);

            assertThat(boundOnly).contains("NEEDS YOUR DECISION ══ 1 open", "║  " + lamp + "   Which lamp?").doesNotContain("Board for");
            assertThat(posted).contains("NEEDS YOUR DECISION ══ 2 open", "║  Board for Work on survey-lamps: 2 questions waiting at " + BoardRounds.LINK);
        }

        @Test
        @Tag("adapter")
        @DisplayName("AT-19 a worker's question waits on EDI, never on the captain, and the steer that settles it clears it once acknowledged")
        void aWorkersQuestionWaitsOnEdi() throws IOException {
            TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY);
            Worker worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
            StatusKey reviewGate = new StatusKey("nm-01RUN-review");
            home.recordStatus().report(worker.id(), StatusState.NEEDS_DECISION, reviewGate, null, "Fix the review finding or skip it?");

            String report = captainReport(home, Turn.NONE);
            String brief = returnBrief(home);
            ConsoleSitrep.Ongoing asked = consoleSitrep(home).ongoing().getFirst();
            home.steerWorker().steer(TaskReference.parse("fit-lamp"), "Fix it.", reviewGate, Actor.PRIMARY);
            ConsoleSitrep.Ongoing steered = consoleSitrep(home).ongoing().getFirst();
            home.workerInbox().read(worker.id());
            home.workerInbox().acknowledge(worker.id(), 1);
            ConsoleSitrep.Ongoing settled = consoleSitrep(home).ongoing().getFirst();

            assertThat(report).contains("nothing open").doesNotContain("review finding");
            assertThat(brief).contains("Needs your decision (0)");
            assertThat(asked).extracting(ConsoleSitrep.Ongoing::state, ConsoleSitrep.Ongoing::doing).containsExactly("with EDI", "EDI is settling a pipeline question");
            assertThat(steered.state()).isEqualTo("with EDI");
            assertThat(settled).extracting(ConsoleSitrep.Ongoing::state, ConsoleSitrep.Ongoing::doing).containsExactly("working", "back at work; EDI settled its question");
        }

        @Test
        @Tag("adapter")
        @DisplayName("AT-19 a request ready for the captain's review waits in the decision zone with its full address until he merges it")
        void aRequestReadyForReviewWaitsOnTheCaptain() throws IOException {
            TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY);
            Worker worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
            home.deliveries().save(Delivery.of(worker.id().task(), request(MergeRequestState.OPEN), TestHome.START));
            home.workers().update(home.workers().get(worker.id()).orElseThrow().reported(new StatusReport(StatusState.DONE, null, null, "Fitted."), TestHome.START));

            String report = captainReport(home, Turn.NONE);
            String brief = returnBrief(home);
            List<ConsoleSitrep.Review> onTheConsole = consoleSitrep(home).reviews();
            home.deliveries().save(home.deliveries().of(worker.id().task()).orElseThrow().observed(request(MergeRequestState.MERGED), TestHome.START));
            String merged = captainReport(home, Turn.NONE);

            assertThat(report).contains("NEEDS YOUR DECISION ══ 1 open", "║  Ready for your review: Work on fit-lamp - " + REQUEST + "\n");
            assertThat(brief).contains("Needs your decision (1)\n  Ready for your review: Work on fit-lamp - " + REQUEST + "\n");
            assertThat(onTheConsole).containsExactly(new ConsoleSitrep.Review("fit-lamp", "Work on fit-lamp", List.of("harness"), REQUEST));
            assertThat(merged).contains("nothing open").doesNotContain("Ready for your review");
        }

        private static MergeRequestView request(MergeRequestState state) {
            MergeRequestAddress address = new MergeRequestAddress(ForgeRepository.registered(ForgeKind.GITHUB, null, "example-owner/harness"), 7);
            return new MergeRequestView(address, state, true, "feature/HRN-3", "main", "abc123", state == MergeRequestState.MERGED ? "def456" : null);
        }

        private static CallingSession captainSays(TestHome home, String words, String task) {
            UUID session = UUID.randomUUID();
            home.primaryHooks().sessionStarted(session, null, true);
            home.primaryHooks().promptSubmitted(session, words);
            CallingSession calling = new CallingSession(session);
            home.captainsWord().record(calling, words, TaskReference.parse(task), Actor.PRIMARY);
            return calling;
        }

        private static BoardPrompt typed(String words) {
            return new BoardPrompt("", "message", "Freeform message", words, "", Map.of(), List.of());
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

    private static ConsoleSitrep consoleSitrep(TestHome home) {
        return new SitrepConsolePresenter().present(home.fleetSnapshot().snapshot(), TestHome.START, "laptop", CaptainAddress.DEFAULT,
                Presence.presentSince(TestHome.START));
    }

    private static String returnBrief(TestHome home) {
        Presence left = Presence.presentSince(TestHome.START).quiet(TestHome.START);
        return new ReturnBriefText().text(home.returnBriefs().since(left));
    }

    private static DecisionNumber raise(TestHome home, String task, String title) {
        return home.raiseDecision().raise(new TaskReference.ById(new TaskId(task)), card(title), Actor.PRIMARY);
    }

    private static void answer(TestHome home, DecisionNumber number) {
        home.answerDecision().answer(new TaskReference.ByDecision(number), new GivenAnswer(number + " A", new OptionKey('A'), "", AnswerChannel.CHAT), Actor.PRIMARY);
    }
}
