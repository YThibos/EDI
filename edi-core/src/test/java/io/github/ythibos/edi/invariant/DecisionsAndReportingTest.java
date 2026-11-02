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

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.ythibos.edi.application.decision.GivenAnswer;
import io.github.ythibos.edi.application.worker.HookAnswer;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.board.BoardCard;
import io.github.ythibos.edi.domain.board.BoardFeedback;
import io.github.ythibos.edi.domain.board.BoardPrompt;
import io.github.ythibos.edi.domain.board.BoardRound;
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
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.primary.SessionSource;
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
import io.github.ythibos.edi.testing.ManualClock;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;
import io.github.ythibos.edi.web.PresenterFixtures;
import io.github.ythibos.edi.web.console.ConsoleSitrep;
import io.github.ythibos.edi.web.report.CaptainReport;
import io.github.ythibos.edi.web.report.ReportWidth;
import io.github.ythibos.edi.web.report.TaskWording;
import io.github.ythibos.edi.web.report.Turn;
import io.github.ythibos.edi.web.report.TurnItem;
import io.github.ythibos.edi.web.report.TurnReport;

@DisplayName("4.4 Decisions and reporting")
class DecisionsAndReportingTest {

    private static final int MIGRATED_DECISION_SEQUENCE = 57;
    private static final int GOLDEN_REPORTS_LINTED = 16;
    private static final int FIXED_KINDS_LINTED = 5;

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
            assertThat(reraisedAfterClosing).isEqualTo(new DecisionNumber(61));
            assertThat(home.decisions().get(afterArchiving).card().title()).isEqualTo("Fit it today?");
            assertThat(home.decisions().get(afterArchiving).isOpen()).isFalse();
            assertThat(home.decisions().all()).extracting(decision -> decision.number().value()).containsExactly(58, 59, 60, 61);
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

        @Test
        @DisplayName("AT-19 an open decision shows its card in the turn report of the turn it is raised and one line in every later turn report")
        void anOpenDecisionStaysInEveryTurnReportOnOneLineOnceShown() throws IOException {
            TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY).continuingDecisionsFrom(MIGRATED_DECISION_SEQUENCE);
            UUID primary = UUID.randomUUID();
            home.primaryHooks().sessionStarted(primary, null, SessionSource.LAUNCH);
            home.primaryHooks().promptSubmitted(primary, "Ask me about the lamp.");
            home.addTask().add(captainItem("lamp-choice", "Choose the lamp"), Actor.PRIMARY);
            DecisionNumber open = raise(home, "lamp-choice", "Which lamp?");

            String raisedTurn = turnReport(home, primary);
            String raisedTurnAgain = turnReport(home, primary);
            home.clock().advance(Duration.ofHours(1));
            home.primaryHooks().promptSubmitted(primary, "Fit the bracket.");
            String laterTurn = turnReport(home, primary);
            home.clock().advance(Duration.ofDays(3));
            home.primaryHooks().promptSubmitted(primary, "<task-notification>\n<status>completed</status>\n</task-notification>");
            home.clock().advance(Duration.ofHours(1));
            home.primaryHooks().promptSubmitted(primary, "How is the lamp?");
            String muchLaterTurn = turnReport(home, primary);

            assertThat(raisedTurn).isEqualTo(raisedTurnAgain)
                    .contains("║  " + open + "   Which lamp?\n", "║        (A)  The simple way\n", "║        » Recommended: A - it is enough\n");
            assertThat(laterTurn).isEqualTo(muchLaterTurn)
                    .contains("║  " + open + "   Which lamp? » Recommended: A - The simple way\n").doesNotContain("(A)", "it is enough");
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
            assertThat(postedOnTheConsole).containsExactly(new ConsoleSitrep.Board("survey-lamps", "Work on survey-lamps", List.of("harness"), 3, BoardRounds.LINK,
                    List.of()));
            assertThat(answered).contains("nothing open").doesNotContain("Board for");
            assertThat(postedAgain).contains("NEEDS YOUR DECISION ══ 1 open", "║  Board for Work on survey-lamps: 1 question waiting at " + BoardRounds.LINK);
        }

        @Test
        @Tag("adapter")
        @DisplayName("AT-19 a decision bound to a waiting board shows as one line under its board in the report, the console and the return brief,"
                + " while a decision bound to no board keeps its full card")
        void aBoardBoundDecisionShowsAsOneLineUnderItsBoard() throws IOException {
            TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY).continuingDecisionsFrom(MIGRATED_DECISION_SEQUENCE);
            Worker scout = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queued("survey-lamps", TaskKind.SCOUT, "harness", "HRN-4"), CategoryId.RESEARCH);
            home.addTask().add(captainItem("paint-choice", "Choose the paint"), Actor.PRIMARY);
            DecisionNumber paint = raise(home, "paint-choice", "Which paint?");
            Path board = Files.writeString(Files.createDirectories(home.home().taskDirectory(scout.id().task())).resolve("board.html"), "<!doctype html>\n");
            home.boardHost().holdsTheNextPollOpen();

            home.armBoard().arm(scout.id(), TaskReference.parse("survey-lamps"), board,
                    new BoardRound(BoardRounds.LINK, 2, List.of(), new BoardCard("lamp", card("Which lamp?"))));
            DecisionNumber lamp = home.decisions().ofTask(new TaskId("survey-lamps")).orElseThrow().number();
            String report = captainReport(home, Turn.NONE);
            ConsoleSitrep console = consoleSitrep(home);

            assertThat(report).contains(
                    "NEEDS YOUR DECISION ══ 3 open",
                    "║  " + paint + "   Which paint?\n║        Both work; one is simpler.\n",
                    "║  Board for Work on survey-lamps: 2 questions waiting at " + BoardRounds.LINK + "\n║        " + lamp + "   Which lamp?\n║\n");
            assertThat(report.substring(report.indexOf("Board for"), report.indexOf("╚"))).doesNotContain("Both work", "(A)", "Recommended");
            assertThat(console.decisions()).extracting(ConsoleSitrep.Card::number).containsExactly(paint.toString());
            assertThat(console.boards()).singleElement().extracting(ConsoleSitrep.Board::decisions).asInstanceOf(InstanceOfAssertFactories.LIST)
                    .containsExactly(new ConsoleSitrep.BoardDecision(lamp.toString(), "Which lamp?"));
            assertThat(returnBrief(home)).contains("Needs your decision (3)\n  " + paint + "  Which paint?\n  Board for Work on survey-lamps: 2 questions "
                    + "waiting at " + BoardRounds.LINK + "\n    " + lamp + "  Which lamp?\n");
            assertThat(PresenterFixtures.sessionStartDigestText().text(home.sessionStartDigest().digestFor(UUID.randomUUID()))).contains("  " + paint
                    + "  paint-choice  Which paint?\n  board  survey-lamps  2 questions at " + BoardRounds.LINK + "\n    " + lamp + "  survey-lamps  Which lamp?\n");
        }

        @Test
        @Tag("adapter")
        @DisplayName("AT-19 the primary puts the new round of a board whose scout stopped before the captain, and a round that asks only bound decisions"
                + " lists them under the board")
        void thePrimaryPutsAStoppedScoutsBoardBeforeTheCaptain() throws IOException {
            TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY).continuingDecisionsFrom(MIGRATED_DECISION_SEQUENCE);
            Worker scout = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queued("survey-lamps", TaskKind.SCOUT, "harness", "HRN-4"), CategoryId.RESEARCH);
            DecisionNumber lamp = raise(home, "survey-lamps", "Which lamp?");
            Path board = Files.writeString(Files.createDirectories(home.home().taskDirectory(scout.id().task())).resolve("board.html"), "<!doctype html>\n");
            home.boardHost().holdsTheNextPollOpen();

            home.armBoard().arm(scout.id(), TaskReference.parse("survey-lamps"), board, BoardRounds.asking(0, lamp));
            String boundOnly = captainReport(home, Turn.NONE);
            String boundOnlyBrief = returnBrief(home);
            String boundOnlyDigest = PresenterFixtures.sessionStartDigestText().text(home.sessionStartDigest().digestFor(UUID.randomUUID()));
            home.workers().update(home.workers().get(scout.id()).orElseThrow().retired(TestHome.START));
            home.postBoardRound().post(board, BoardRounds.LINK, 2, Actor.PRIMARY);
            String posted = captainReport(home, Turn.NONE);

            assertThat(boundOnly).contains("NEEDS YOUR DECISION ══ 1 open",
                    "║  Board for Work on survey-lamps: waiting at " + BoardRounds.LINK + "\n║        " + lamp + "   Which lamp?\n║\n");
            assertThat(boundOnly).doesNotContain("Both work", "(A)", "Recommended");
            assertThat(boundOnlyBrief).contains("  Board for Work on survey-lamps: waiting at " + BoardRounds.LINK + "\n    " + lamp + "  Which lamp?\n");
            assertThat(boundOnlyDigest).contains("Waiting on the captain (1)\n  board  survey-lamps  " + BoardRounds.LINK + "\n    " + lamp
                    + "  survey-lamps  Which lamp?\n");
            assertThat(posted).contains("NEEDS YOUR DECISION ══ 2 open",
                    "║  Board for Work on survey-lamps: 2 questions waiting at " + BoardRounds.LINK + "\n║        " + lamp + "   Which lamp?\n║\n");
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
            assertThat(asked).extracting(ConsoleSitrep.Ongoing::state, ConsoleSitrep.Ongoing::doing).containsExactly("with me", "I am settling a question");
            assertThat(steered.state()).isEqualTo("with me");
            assertThat(settled).extracting(ConsoleSitrep.Ongoing::state, ConsoleSitrep.Ongoing::doing).containsExactly("working", "back at work; I settled its question");
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

        @Test
        @Tag("adapter")
        @DisplayName("AT-19 a request whose pipeline still runs or whose steer waits unacknowledged after its done shows as changing with the reason,"
                + " never as ready")
        void aRequestStillChangingAfterItsDoneShowsAsChanging() throws IOException {
            TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY);
            Worker worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
            home.deliveries().save(Delivery.of(worker.id().task(), request(MergeRequestState.OPEN), TestHome.START));
            home.workers().update(home.workers().get(worker.id()).orElseThrow().reported(new StatusReport(StatusState.DONE, null, null, "Fitted."), TestHome.START));
            home.pipelineRuns().save(worker.id().task(), new PipelineRun("01RUN", "running", "review", "abc123", REQUEST), TestHome.START);
            home.steerWorker().steer(TaskReference.parse("fit-lamp"), "Amend the record too.", null, Actor.PRIMARY);

            String report = captainReport(home, Turn.NONE);
            String brief = returnBrief(home);
            ConsoleSitrep onTheConsole = consoleSitrep(home);
            String digest = PresenterFixtures.sessionStartDigestText().text(home.sessionStartDigest().digestFor(UUID.randomUUID()));
            home.pipelineRuns().save(worker.id().task(), new PipelineRun("01RUN", "completed", null, "abc123", REQUEST), TestHome.START);
            home.workerInbox().acknowledge(worker.id(), 1);
            String settled = captainReport(home, Turn.NONE);

            String changing = "Changing, hold the merge: Work on fit-lamp (pipeline review; steer 1 not acknowledged) - " + REQUEST;
            assertThat(report).contains("NEEDS YOUR DECISION ══ 1 open",
                    "║  Changing, hold the merge: Work on fit-lamp (pipeline review; steer 1 not acknowledged) -\n║        " + REQUEST + "\n")
                    .doesNotContain("Ready for your review");
            assertThat(brief).contains("Needs your decision (1)\n  " + changing + "\n");
            assertThat(digest).contains("Waiting on the captain (1)\n  changing  fit-lamp  " + REQUEST
                    + "  hold the merge: pipeline review; steer 1 not acknowledged\n");
            assertThat(onTheConsole.reviews()).isEmpty();
            assertThat(onTheConsole.changing()).containsExactly(new ConsoleSitrep.Changing("fit-lamp", "Work on fit-lamp", List.of("harness"), REQUEST,
                    "pipeline review; steer 1 not acknowledged"));
            assertThat(settled).contains("║  Ready for your review: Work on fit-lamp - " + REQUEST + "\n").doesNotContain("Changing");
        }

        private static MergeRequestView request(MergeRequestState state) {
            MergeRequestAddress address = new MergeRequestAddress(ForgeRepository.registered(ForgeKind.GITHUB, null, "example-owner/harness"), 7);
            boolean merged = state == MergeRequestState.MERGED;
            return new MergeRequestView(address, state, true, "feature/HRN-3", "main", "abc123", merged ? "def456" : null, merged ? TestHome.START : null);
        }

        private static CallingSession captainSays(TestHome home, String words, String task) {
            UUID session = UUID.randomUUID();
            home.primaryHooks().sessionStarted(session, null, SessionSource.LAUNCH);
            home.primaryHooks().promptSubmitted(session, words);
            CallingSession calling = new CallingSession(session);
            home.captainsWord().record(calling, words, TaskReference.parse(task), null, Actor.PRIMARY);
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
    @DisplayName("I-21 Every state-changing turn ends with the turn report")
    class StateChangingTurnsEndWithTheReport {

        @Test
        @DisplayName("AT-21 the primary's Stop hook blocks a state-changing turn until edi report ran in that turn")
        void theStopHookWaitsForTheReport() throws IOException {
            TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY);
            UUID primary = UUID.randomUUID();
            home.primaryHooks().sessionStarted(primary, null, SessionSource.LAUNCH);
            home.primaryHooks().promptSubmitted(primary, "What is waiting on me?");
            HookAnswer quietTurn = home.primaryHooks().stopping(primary);

            home.primaryHooks().promptSubmitted(primary, "Queue the brass lamp.");
            home.addTask().add(ship("fit-lamp", "Fit the brass lamp"), Actor.PRIMARY);
            HookAnswer unreported = home.primaryHooks().stopping(primary);
            home.primaryHooks().reportRendered(primary);
            HookAnswer reported = home.primaryHooks().stopping(primary);

            assertThat(quietTurn).isEqualTo(HookAnswer.PROCEED);
            assertThat(unreported.verdict()).isEqualTo(HookAnswer.Verdict.BLOCK_THE_STOP);
            assertThat(unreported.reason()).isEqualTo("This turn changed state: render the turn report with edi report --turn - and paste it verbatim, then stop.");
            assertThat(reported).isEqualTo(HookAnswer.PROCEED);
        }
    }

    @Nested
    @DisplayName("I-22 Talk in outcomes")
    class TalkInOutcomes {

        @Test
        @DisplayName("AT-22 the lint finds every internal label and passes a quoted name, a path and a link")
        void theLintRejectsTheInternalLabelList() {
            assertThat(CaptainVoice.INTERNAL_LABELS).allSatisfy(label -> assertThat(CaptainVoice.internalLabelsIn("The " + label.label() + " is ready."))
                    .as(label.label()).contains(label));
            assertThat(CaptainVoice.internalLabelsIn("The `worktree` at /home/example/worktrees/lamp, \"Fix the worktree\" and https://example.org/wake"))
                    .isEmpty();
            assertThatThrownBy(() -> CaptainVoice.requireOutcomes("A decision card", "Discard the worktree of the lamp?"))
                    .isInstanceOf(Refusal.class)
                    .hasMessage("A decision card speaks to the captain in outcomes: say \"isolated copy\" instead of \"worktree\", or quote a name in"
                            + " double quotes when it is one (I-22).");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.ythibos.edi.invariant.CaptainFacingStrings#everyKindEdiGenerates")
        @Tag("adapter")
        @DisplayName("AT-22 a lint over captain-facing strings rejects the internal-label list")
        void captainFacingStringsCarryNoInternalLabels(CaptainFacingStrings.GeneratedStrings kind) throws IOException {
            List<String> generated = kind.in(directory);

            assertThat(generated).isNotEmpty()
                    .allSatisfy(text -> assertThat(CaptainVoice.internalLabelsIn(text)).as(text).isEmpty());
        }

        @Test
        @DisplayName("AT-22 the persona check finds EDI named in the third person in a message template, and passes a work key, a command and quoted text")
        void thePersonaCheckFindsEdiInTheThirdPerson() {
            assertThat(thirdPersonIn("case DECISION_RAISED -> wake.task() + \": EDI raised \" + wake.detail() + \"; put it before the captain.\";"))
                    .containsExactly("EDI raised");
            assertThat(thirdPersonIn("case DECISION_RAISED -> wake.task() + \": You raised \" + wake.detail() + \"; put it before the captain.\";"))
                    .isEmpty();
            assertThat(thirdPersonIn("return \"EDI-74 merged; run edi report, or `edi report`, as \\\"EDI raised D43\\\" says.\";")).isEmpty();
        }

        @Test
        @DisplayName("AT-22 no message template the primary or the captain reads names EDI in the third person")
        void messageTemplatesSpeakAsEdi() {
            assertThat(MessageTemplates.readByThePrimaryOrTheCaptain()).isNotEmpty()
                    .filteredOn(template -> !ThirdPersonSelf.in(template.text()).isEmpty())
                    .isEmpty();
        }

        @Test
        @DisplayName("AT-22 the lint covers every golden report and every fixed kind of captain-facing string")
        void theLintCoversEveryKindEdiGenerates() {
            assertThat(CaptainFacingStrings.everyKindEdiGenerates()).hasSizeGreaterThanOrEqualTo(GOLDEN_REPORTS_LINTED + FIXED_KINDS_LINTED);
        }
    }

    private static List<String> thirdPersonIn(String code) {
        return MessageTemplates.javaTemplatesIn("WakeDigestText.java", code).stream().flatMap(template -> ThirdPersonSelf.in(template.text()).stream()).toList();
    }

    private static String captainReport(TestHome home, Turn turn) {
        return PresenterFixtures.captainReportPresenter().text(new CaptainReport(home.fleetSnapshot().snapshot(), turn, ReportWidth.standard(), Presence.presentSince(TestHome.START),
                null, null));
    }

    private static String turnReport(TestHome home, UUID session) {
        TurnReport report = new TurnReport(home.fleetSnapshot().snapshot().waiting(), Turn.NONE, ReportWidth.standard(), null,
                home.primarySessions().previousReportOf(session).orElse(null));
        home.primaryHooks().reportRendered(session);
        return PresenterFixtures.turnReportPresenter().text(report);
    }

    private static ConsoleSitrep consoleSitrep(TestHome home) {
        return PresenterFixtures.sitrepConsolePresenter().present(home.fleetSnapshot().snapshot(), TestHome.START, ManualClock.UTC, "laptop", CaptainAddress.DEFAULT,
                Presence.presentSince(TestHome.START));
    }

    private static String returnBrief(TestHome home) {
        Presence left = Presence.presentSince(TestHome.START).quiet(TestHome.START);
        return PresenterFixtures.returnBriefText().text(home.returnBriefs().since(left));
    }

    private static DecisionNumber raise(TestHome home, String task, String title) {
        return home.raiseDecision().raise(new TaskReference.ById(new TaskId(task)), card(title), Actor.PRIMARY);
    }

    private static void answer(TestHome home, DecisionNumber number) {
        home.answerDecision().answer(new TaskReference.ByDecision(number), new GivenAnswer(number + " A", new OptionKey('A'), "", AnswerChannel.CHAT), Actor.PRIMARY);
    }
}
