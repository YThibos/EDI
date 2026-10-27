package io.github.ythibos.edi.application.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.decision.GivenAnswer;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CaptainWordAct;
import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.board.BoardAttachment;
import io.github.ythibos.edi.domain.board.BoardBinding;
import io.github.ythibos.edi.domain.board.BoardCard;
import io.github.ythibos.edi.domain.board.BoardFeedback;
import io.github.ythibos.edi.domain.board.BoardPrompt;
import io.github.ythibos.edi.domain.board.BoardRound;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.BoardRounds;
import io.github.ythibos.edi.testing.Cards;
import io.github.ythibos.edi.testing.FakeBoardHost;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class ResearchBoardsTest {

    private static final TaskId SCOUT = new TaskId("lantern-research");
    private static final TaskId LAMP_CHOICE = new TaskId("choose-lamp");
    private static final int ROUNDS_UNTIL_THE_LISTENER_RESTARTS = 50;
    private static final Duration ROUND_PATIENCE = Duration.ofMillis(100);
    private static final String CLOSE_OUT = "close-out";
    private static final BoardCard CLOSE_OUT_CARD = new BoardCard(CLOSE_OUT, Cards.authorising("Close the lantern work out?", CaptainWordAct.PROMOTE));

    @TempDir
    private Path directory;

    private TestHome home;
    private Worker scout;
    private Path boardFile;
    private DecisionNumber decision;

    @BeforeEach
    void prepareAScoutWithABoardAndADecision() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        scout = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queued(SCOUT.value(), TaskKind.SCOUT, "harness", "HRN-4"), CategoryId.RESEARCH);
        boardFile = Files.writeString(Files.createDirectories(home.home().taskDirectory(SCOUT)).resolve("board.html"), "<!doctype html>\n");
        home.storing(List.of(WorkerFixtures.queued(LAMP_CHOICE.value(), TaskKind.CAPTAIN, "harness", null)));
        decision = home.raiseDecision().raise(new TaskReference.ById(LAMP_CHOICE), Cards.card("Which lamp?"), Actor.PRIMARY);
    }

    @Test
    void armsABoardInItsTasksDirectoryAndStartsListeningToIt() throws InterruptedException, IOException {
        var polled = home.boardHost().expectPolls(1);

        Board armed = arm(boardFile, decision);

        FakeBoardHost.await(polled);
        assertThat(armed.bound()).containsExactly(decision);
        assertThat(armed.file()).isEqualTo(boardFile.toRealPath().toString());
        assertThat(home.boardHost().listeners()).first().isEqualTo("edi:lantern-research#" + armed.id());
        assertThat(home.eventTypesOf(SCOUT)).endsWith("BoardArmed");
    }

    @Test
    void letsThePrimaryPutANewRoundOfAListeningBoardBeforeTheCaptainWhenItsScoutCannotArmItAgain() {
        Board armed = armQuietly(boardFile, decision);
        home.workers().update(home.workers().get(scout.id()).orElseThrow().retired(TestHome.START));

        Board posted = home.postBoardRound().post(boardFile, "http://127.0.0.1:4387/session/9f8e7d", 2, Actor.PRIMARY);

        assertThat(posted.id()).isEqualTo(armed.id());
        assertThat(posted.waitsOnTheCaptain()).isTrue();
        assertThat(posted.openQuestions()).isEqualTo(2);
        assertThat(posted.link()).isEqualTo("http://127.0.0.1:4387/session/9f8e7d");
        assertThat(posted.bound()).containsExactly(decision);
        assertThat(home.eventTypesOf(SCOUT)).endsWith("BoardRoundPosted");
        assertThat(home.boards().get(armed.id())).contains(posted);
    }

    @Test
    void refusesARoundOnAFileWhereNoBoardListens() throws IOException {
        Path unarmed = Files.writeString(home.home().taskDirectory(SCOUT).resolve("other.html"), "<!doctype html>\n");

        assertThatThrownBy(() -> home.postBoardRound().post(unarmed, BoardRounds.LINK, 1, Actor.PRIMARY)).isInstanceOf(Refusal.class)
                .hasMessage("No research board listens at " + unarmed + "; its scout arms it with edi board arm.");
    }

    @Test
    void rebindsABoardThatIsArmedAgainInsteadOfListeningTwice() {
        Board first = arm(boardFile);

        Board again = arm(boardFile, decision);

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(home.boards().listening()).singleElement().extracting(Board::bound).isEqualTo(List.of(decision));
    }

    @Test
    void refusesABoardOutsideItsTaskOrForAnotherTaskOrAnAnsweredDecision() throws IOException {
        Path elsewhere = Files.writeString(directory.resolve("board.html"), "<!doctype html>\n");
        answerInChat();

        assertThatThrownBy(() -> arm(elsewhere)).isInstanceOf(Refusal.class).hasMessageContaining("lives in its task's directory");
        assertThatThrownBy(() -> arm(boardFile, decision)).isInstanceOf(Refusal.class)
                .hasMessage(decision + " is already answered; a board answers only open decisions.");
        assertThatThrownBy(() -> home.armBoard().arm(scout.id(), new TaskReference.ById(LAMP_CHOICE), boardFile, BoardRounds.binding()))
                .isInstanceOf(Refusal.class)
                .hasMessage("lantern-research#1 arms boards for its own task, lantern-research, not for choose-lamp.");
    }

    @Test
    void passesARoundToTheWorkersInboxAndRecordsTheBoundAnswerAsTheCaptainsWord() {
        Board board = armQuietly(boardFile, decision);
        BoardPrompt choice = new BoardPrompt("3", "choice", decision + ": option=B", decision + ": option=B", "section#s2 > form",
                contextOf("decision", decision.toString(), "option", "B"), List.of());
        BoardPrompt annotation = new BoardPrompt("1", "img", "Deck lighting at night", "Too faint in the corner", "section#s1 > img", Map.of(),
                List.of(new BoardAttachment("/tmp/capture.png", "image/png", "capture.png")));

        boolean pollAgain = home.receiveBoardFeedback().receive(board.id(), new BoardFeedback.Answered(List.of(annotation, choice), false));

        assertThat(pollAgain).isTrue();
        assertThat(message(1).body()).isEqualTo("""
                The captain answered on your research board %s, round 1.
                Prompt 1 (img, at section#s1 > img): Too faint in the corner
                  on: Deck lighting at night
                  attachment: /tmp/capture.png (image/png)
                Prompt 3 (choice, at section#s2 > form): %s: option=B
                  context: decision=%s, option=B
                EDI hands these to their decisions as the captain's answers: %s option B.""".formatted(boardFile, decision, decision, decision));
        CaptainAnswer recorded = home.decisions().get(decision).answer();
        assertThat(recorded.verbatim()).isEqualTo(decision + ": option=B");
        assertThat(recorded.option()).isEqualTo(new OptionKey('B'));
        assertThat(recorded.channel()).isEqualTo(AnswerChannel.BOARD);
        assertThat(home.boards().unsettledAnswers()).isEmpty();
        assertThat(home.eventTypesOf(SCOUT)).endsWith("SteerSent", "BoardAnswered");
        assertThat(home.wakes().unacknowledged()).extracting(wake -> wake.wake().kind())
                .contains(WakeKind.BOARD_ANSWERED, WakeKind.DECISION_ANSWERED);
    }

    @Test
    void raisesTheCardARoundCarriesOnItsOwnTaskAndBindsItUnderTheQuestionThatAnswersIt() {
        Board armed = armQuietlyCarrying(CLOSE_OUT_CARD);

        DecisionNumber raised = home.decisions().ofTask(SCOUT).orElseThrow().number();
        assertThat(armed.bindings()).containsExactly(new BoardBinding(raised, CLOSE_OUT));
        assertThat(home.boards().get(armed.id())).contains(armed);
        assertThat(home.decisions().get(raised).card()).isEqualTo(CLOSE_OUT_CARD.card());
        assertThat(home.decisions().get(raised).isOpen()).isTrue();
        assertThat(home.eventTypesOf(SCOUT)).endsWith("DecisionRaised", "BoardArmed");
        assertThat(home.jdbc().sql("select actor from event where type = 'DecisionRaised' and task_id = :task").param("task", SCOUT.value())
                .query(String.class).single()).isEqualTo("worker:" + scout.id());
    }

    @Test
    void answersTheRaisedCardWithTheBoardsWordsWhenTheCaptainChoosesOnTheBoard() {
        Board armed = armQuietlyCarrying(CLOSE_OUT_CARD);

        home.receiveBoardFeedback().receive(armed.id(), new BoardFeedback.Answered(List.of(closeOutChosen("A")), false));

        CaptainAnswer recorded = home.decisions().ofTask(SCOUT).orElseThrow().answer();
        assertThat(recorded.verbatim()).isEqualTo("Close-out decision: option A");
        assertThat(recorded.option()).isEqualTo(new OptionKey('A'));
        assertThat(recorded.channel()).isEqualTo(AnswerChannel.BOARD);
        assertThat(home.boards().answersOf(armed.id())).singleElement().satisfies(answer -> assertThat(answer.refusedBecause()).isNull());
    }

    @Test
    void refusesToRaiseTheCardAgainOnceTheCaptainAnsweredIt() {
        Board armed = armQuietlyCarrying(CLOSE_OUT_CARD);
        home.receiveBoardFeedback().receive(armed.id(), new BoardFeedback.Answered(List.of(closeOutChosen("A")), false));
        DecisionNumber answered = armed.bound().getFirst();

        assertThatThrownBy(() -> armQuietlyCarrying(CLOSE_OUT_CARD)).isInstanceOf(Refusal.class)
                .hasMessage(answered + " on lantern-research already holds the captain's answer to \"Close the lantern work out?\" (\"Close-out "
                        + "decision: option A\"), so a board round raises it no more; arm the round without --card, or give a new question its own title.");
        assertThat(home.decisions().get(answered).answer().option()).isEqualTo(new OptionKey('A'));
    }

    @Test
    void raisesNothingWhenARoundCarriesTheAnsweredCardWithNewContextAndTellsTheScout() {
        Board armed = armQuietlyCarrying(CLOSE_OUT_CARD);
        home.receiveBoardFeedback().receive(armed.id(), new BoardFeedback.Answered(List.of(closeOutChosen("A")), false));
        DecisionNumber answered = armed.bound().getFirst();
        DecisionCard original = CLOSE_OUT_CARD.card();
        BoardCard withNewEvidence = new BoardCard(CLOSE_OUT, new DecisionCard(original.title(), original.context() + " The 2026 data agrees.",
                original.options(), original.recommendation()));
        int raisedBefore = home.decisions().lastNumberIssued();

        assertThatThrownBy(() -> armQuietlyCarrying(withNewEvidence)).isInstanceOf(Refusal.class)
                .hasMessageStartingWith(answered + " on lantern-research already holds the captain's answer to \"Close the lantern work out?\"");
        assertThat(home.decisions().lastNumberIssued()).isEqualTo(raisedBefore);
        assertThat(home.decisions().ofTask(SCOUT).orElseThrow().number()).isEqualTo(answered);
    }

    @Test
    void raisesNothingForAnAnsweredCardEvenAfterALaterQuestionOnItsTaskWasAnswered() {
        Board armed = armQuietlyCarrying(CLOSE_OUT_CARD);
        home.receiveBoardFeedback().receive(armed.id(), new BoardFeedback.Answered(List.of(closeOutChosen("A")), false));
        DecisionNumber answered = armed.bound().getFirst();
        DecisionNumber later = armQuietlyCarrying(new BoardCard("spare-bulbs", Cards.card("Order spare bulbs?"))).bound().getLast();
        home.answerDecision().answer(new TaskReference.ByDecision(later), new GivenAnswer(later + " A", new OptionKey('A'), "", AnswerChannel.CHAT),
                Actor.PRIMARY);

        assertThatThrownBy(() -> armQuietlyCarrying(CLOSE_OUT_CARD)).isInstanceOf(Refusal.class)
                .hasMessageStartingWith(answered + " on lantern-research already holds the captain's answer");
        assertThat(home.decisions().ofTask(SCOUT).orElseThrow().number()).isEqualTo(later);
    }

    @Test
    void raisesANewCardAfterTheCaptainAnsweredTheLastAsANewDecision() {
        Board armed = armQuietlyCarrying(CLOSE_OUT_CARD);
        home.receiveBoardFeedback().receive(armed.id(), new BoardFeedback.Answered(List.of(closeOutChosen("A")), false));
        DecisionNumber answered = armed.bound().getFirst();
        BoardCard nextQuestion = new BoardCard("spare-bulbs", Cards.card("Order spare bulbs?"));

        Board again = armQuietlyCarrying(nextQuestion);

        DecisionNumber raised = home.decisions().ofTask(SCOUT).orElseThrow().number();
        assertThat(raised).isNotEqualTo(answered);
        assertThat(again.bindings()).contains(new BoardBinding(raised, "spare-bulbs"));
        assertThat(home.decisions().get(answered).answer().verbatim()).isEqualTo("Close-out decision: option A");
    }

    @Test
    void raisesTheRevisedCardOfItsOwnBoardAgainUnderTheSameNumberWhileItIsOpen() {
        DecisionNumber first = armQuietlyCarrying(CLOSE_OUT_CARD).bound().getFirst();
        BoardCard revised = new BoardCard(CLOSE_OUT, Cards.authorising("Close the lantern work out now?", CaptainWordAct.PROMOTE));

        Board again = armQuietlyCarrying(revised);

        assertThat(again.bindings()).containsExactly(new BoardBinding(first, CLOSE_OUT));
        assertThat(home.decisions().get(first).card()).isEqualTo(revised.card());
    }

    @Test
    void refusesACardThatWouldReplaceADecisionTheTaskAlreadyHolds() {
        DecisionNumber held = home.raiseDecision().raise(new TaskReference.ById(SCOUT), Cards.card("Drop the lantern work?"), Actor.PRIMARY);

        assertThatThrownBy(() -> armQuietlyCarrying(CLOSE_OUT_CARD)).isInstanceOf(Refusal.class)
                .hasMessage(held + " already waits on the captain on lantern-research (\"Drop the lantern work?\"); if the board answers it, arm the "
                        + "round with that very card under --card, or report the new question to EDI with edi status needs-decision --key <question>.");
        assertThat(home.decisions().get(held).card().title()).isEqualTo("Drop the lantern work?");
        assertThat(home.boards().listening()).isEmpty();
    }

    @Test
    void keepsTheCardBoundUnderItsQuestionWhenTheBoardIsArmedAgainWithoutItOrByItsNumber() {
        DecisionNumber raised = armQuietlyCarrying(CLOSE_OUT_CARD).bound().getFirst();
        armQuietly(boardFile, raised);

        Board again = home.armBoard().arm(scout.id(), new TaskReference.ById(SCOUT), boardFile, BoardRounds.asking(2));
        home.receiveBoardFeedback().receive(again.id(), new BoardFeedback.Answered(List.of(closeOutChosen("B")), false));

        assertThat(again.bindings()).containsExactly(new BoardBinding(raised, CLOSE_OUT));
        assertThat(home.decisions().get(raised).answer().option()).isEqualTo(new OptionKey('B'));
        assertThat(home.decisions().get(raised).answer().channel()).isEqualTo(AnswerChannel.BOARD);
    }

    @Test
    void raisesItsOwnOpenCardAgainOnABoardArmedAfreshOnceTheSessionEnded() {
        Board first = armQuietlyCarrying(CLOSE_OUT_CARD);
        DecisionNumber raised = first.bound().getFirst();
        home.receiveBoardFeedback().receive(first.id(), new BoardFeedback.ClosedEmpty());

        Board fresh = armQuietlyCarrying(CLOSE_OUT_CARD);
        home.receiveBoardFeedback().receive(fresh.id(), new BoardFeedback.Answered(List.of(closeOutChosen("A")), false));

        assertThat(fresh.id()).isNotEqualTo(first.id());
        assertThat(fresh.bindings()).containsExactly(new BoardBinding(raised, CLOSE_OUT));
        assertThat(home.decisions().get(raised).answer().option()).isEqualTo(new OptionKey('A'));
    }

    @Test
    void refusesABoardAnswerToAnOlderAnsweredCardInsteadOfAnsweringTheNewerOneOnItsTask() {
        Board armed = armQuietlyCarrying(CLOSE_OUT_CARD);
        DecisionNumber older = armed.bound().getFirst();
        home.boards().save(armed.answeredOnceMore().answersIn(new BoardFeedback.Answered(List.of(closeOutChosen("B")), false),
                number -> home.decisions().get(number).card(), TestHome.START).getFirst());
        home.answerDecision().answer(new TaskReference.ByDecision(older), new GivenAnswer("A: close now", new OptionKey('A'), "", AnswerChannel.CONSOLE),
                Actor.CAPTAIN);
        DecisionNumber newer = armQuietlyCarrying(new BoardCard("spare-bulbs", Cards.card("Order spare bulbs?"))).bound().getLast();

        home.settleBoardAnswers().settleEveryUnsettled();

        assertThat(home.decisions().get(newer).isOpen()).isTrue();
        assertThat(home.decisions().get(older).answer().option()).isEqualTo(new OptionKey('A'));
        assertThat(home.boards().answersOf(armed.id())).singleElement()
                .satisfies(answer -> assertThat(answer.refusedBecause()).isEqualTo(older + " is already answered: A: close now"));
    }

    @Test
    void refusesABoardAnswerToARaisedCardTheCaptainAnsweredInTheConsoleMeanwhile() {
        Board armed = armQuietlyCarrying(CLOSE_OUT_CARD);
        DecisionNumber raised = armed.bound().getFirst();
        home.answerDecision().answer(new TaskReference.ByDecision(raised), new GivenAnswer("B: keep it", new OptionKey('B'), "", AnswerChannel.CONSOLE),
                Actor.CAPTAIN);

        home.receiveBoardFeedback().receive(armed.id(), new BoardFeedback.Answered(List.of(closeOutChosen("A")), false));

        assertThat(home.decisions().get(raised).answer().channel()).isEqualTo(AnswerChannel.CONSOLE);
        assertThat(home.boards().answersOf(armed.id())).singleElement()
                .satisfies(answer -> assertThat(answer.refusedBecause()).isEqualTo(raised + " is already answered: B: keep it"));
        assertThat(home.wakes().unacknowledged()).extracting(wake -> wake.wake().kind()).contains(WakeKind.BOARD_ANSWER_REFUSED);
    }

    @Test
    void stopsListeningOnceTheCaptainEndsTheSessionAndTakesAnEmptyCloseAsNoNews() throws IOException {
        Board answered = armQuietly(boardFile);
        Path second = Files.writeString(boardFile.resolveSibling("round-two.html"), "<!doctype html>\n");
        Board closedEmpty = armQuietly(second);

        boolean afterLastWords = home.receiveBoardFeedback().receive(answered.id(), new BoardFeedback.Answered(List.of(farewell()), true));
        boolean afterEmptyClose = home.receiveBoardFeedback().receive(closedEmpty.id(), new BoardFeedback.ClosedEmpty());

        assertThat(afterLastWords).isFalse();
        assertThat(afterEmptyClose).isFalse();
        assertThat(home.boards().listening()).isEmpty();
        assertThat(message(1).body()).endsWith("The captain ended the board session, so EDI no longer listens to it.");
        assertThat(home.inbox().get(SCOUT, 2)).isEmpty();
        assertThat(home.eventTypesOf(SCOUT)).endsWith("BoardAnswered", "BoardClosed");
    }

    @Test
    void recordsNothingForADisconnectAFailureOrARoundThatArrivesAfterTheBoardStopped() {
        Board board = armQuietly(boardFile);
        home.receiveBoardFeedback().receive(board.id(), new BoardFeedback.ClosedEmpty());

        assertThat(home.receiveBoardFeedback().receive(board.id(), new BoardFeedback.Disconnected())).isFalse();
        assertThat(home.receiveBoardFeedback().receive(board.id(), new BoardFeedback.Failed("the server restarted"))).isFalse();
        assertThat(home.receiveBoardFeedback().receive(board.id(), new BoardFeedback.Answered(List.of(farewell()), false))).isFalse();
        assertThat(home.inbox().unacknowledgedOf(SCOUT)).isEmpty();
    }

    @Test
    void refusesABoardAnswerToADecisionAnsweredMeanwhileAndTellsThePrimary() {
        Board board = armQuietly(boardFile, decision);
        answerInChat();

        home.receiveBoardFeedback().receive(board.id(), new BoardFeedback.Answered(List.of(typed(decision + " B")), false));

        assertThat(home.decisions().get(decision).answer().channel()).isEqualTo(AnswerChannel.CHAT);
        assertThat(home.boards().answersOf(board.id())).singleElement()
                .satisfies(answer -> assertThat(answer.refusedBecause()).startsWith(decision + " is already answered"));
        assertThat(home.wakes().unacknowledged()).extracting(wake -> wake.wake().kind()).contains(WakeKind.BOARD_ANSWER_REFUSED);
    }

    @Test
    void tellsTheScoutThatABoardAnswerToAnAlreadyAnsweredDecisionWasRefusedNotRecorded() {
        Board board = armQuietly(boardFile, decision);
        answerInChat();

        home.receiveBoardFeedback().receive(board.id(), new BoardFeedback.Answered(List.of(typed(decision + " B")), false));

        assertThat(message(1).body()).doesNotContain("records").endsWith("EDI hands these to their decisions as the captain's answers: %s option B."
                .formatted(decision));
        assertThat(message(2).body()).isEqualTo("EDI refused the captain's board answer to %s (\"%s B\"): %s is already answered: %s A"
                .formatted(decision, decision, decision, decision));
        assertThat(home.decisions().get(decision).answer().option()).isEqualTo(new OptionKey('A'));
    }

    @Test
    void tellsTheScoutThatABoardAnswerWithAnOptionTheCardDoesNotOfferWasRefused() {
        Board board = armQuietly(boardFile, decision);
        BoardPrompt choice = new BoardPrompt("4", "choice", decision + ": option=Z", decision + ": option=Z", "section#s2 > form",
                contextOf("decision", decision.toString(), "option", "Z"), List.of());

        home.receiveBoardFeedback().receive(board.id(), new BoardFeedback.Answered(List.of(choice), false));

        assertThat(home.decisions().get(decision).isOpen()).isTrue();
        assertThat(message(2).body()).isEqualTo("EDI refused the captain's board answer to %s (\"%s: option=Z\"): %s offers no option Z."
                .formatted(decision, decision, decision));
        assertThat(home.boards().answersOf(board.id())).singleElement()
                .satisfies(answer -> assertThat(answer.refusedBecause()).isEqualTo(decision + " offers no option Z."));
        assertThat(home.wakes().unacknowledged()).extracting(wake -> wake.wake().kind()).contains(WakeKind.BOARD_ANSWER_REFUSED);
    }

    @Test
    void settlesABoardAnswerAfterACrashWithoutAnsweringTwice() {
        Board board = armQuietly(boardFile, decision);
        home.boards().save(board.answeredOnceMore().answersIn(new BoardFeedback.Answered(List.of(typed(decision + " B")), false),
                number -> home.decisions().get(number).card(), TestHome.START).getFirst());
        home.answerDecision().answer(new TaskReference.ByDecision(decision), new GivenAnswer(decision + " B", new OptionKey('B'), "", AnswerChannel.BOARD),
                Actor.CAPTAIN);

        home.settleBoardAnswers().settleEveryUnsettled();

        assertThat(home.boards().unsettledAnswers()).isEmpty();
        assertThat(home.boards().answersOf(board.id())).singleElement().satisfies(answer -> assertThat(answer.refusedBecause()).isNull());
    }

    @Test
    void keepsARoundUnreadInTheInboxOfAWorkerWhoseWindowIsGone() {
        Board board = armQuietly(boardFile);
        home.runtime().closeEveryWindow();

        home.receiveBoardFeedback().receive(board.id(), new BoardFeedback.Answered(List.of(farewell()), false));

        assertThat(message(1).isReceived()).isFalse();
        assertThat(home.runtime().typedLines()).isEmpty();
    }

    @Test
    void restartsAListenerThatStoppedInTheNextRound() throws InterruptedException {
        var firstPoll = home.boardHost().expectPolls(1);
        Board board = arm(boardFile);
        FakeBoardHost.await(firstPoll);

        var secondPoll = home.boardHost().expectPolls(1);
        for (int round = 0; round < ROUNDS_UNTIL_THE_LISTENER_RESTARTS && secondPoll.getCount() > 0; round++) {
            home.boardListeners().keepListening();
            secondPoll.await(ROUND_PATIENCE.toMillis(), TimeUnit.MILLISECONDS);
        }

        FakeBoardHost.await(secondPoll);
        assertThat(home.boardHost().listeners()).hasSizeGreaterThanOrEqualTo(2).allMatch(board.listener()::equals);
    }

    @Test
    void stopsTheListenerOfABoardWhoseTaskIsForgotten() throws InterruptedException {
        home.boardHost().holdsTheNextPollOpen();
        var polled = home.boardHost().expectPolls(1);
        arm(boardFile);
        FakeBoardHost.await(polled);

        home.boardListeners().forget(SCOUT);

        home.boardHost().awaitStopOfTheHeldPoll();
    }

    private Board arm(Path file, DecisionNumber... bound) {
        return home.armBoard().arm(scout.id(), new TaskReference.ById(SCOUT), file, BoardRounds.binding(bound));
    }

    private Board armQuietly(Path file, DecisionNumber... bound) {
        home.boardHost().holdsTheNextPollOpen();
        return arm(file, bound);
    }

    private void answerInChat() {
        home.answerDecision().answer(new TaskReference.ByDecision(decision), new GivenAnswer(decision + " A", new OptionKey('A'), "", AnswerChannel.CHAT),
                Actor.PRIMARY);
    }

    private InboxMessage message(int number) {
        return home.inbox().get(SCOUT, number).orElseThrow();
    }

    private static Map<String, String> contextOf(String firstField, String firstValue, String secondField, String secondValue) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put(firstField, firstValue);
        context.put(secondField, secondValue);
        return context;
    }

    private Board armQuietlyCarrying(BoardCard card) {
        home.boardHost().holdsTheNextPollOpen();
        return home.armBoard().arm(scout.id(), new TaskReference.ById(SCOUT), boardFile, new BoardRound(BoardRounds.LINK, 0, List.of(), card));
    }

    private static BoardPrompt closeOutChosen(String option) {
        return new BoardPrompt("7", "choice", "Close out: " + option, "Close-out decision: option " + option, "section#decision > form",
                contextOf("question", CLOSE_OUT, "answer", option), List.of());
    }

    private static BoardPrompt farewell() {
        return typed("All done for now.");
    }

    private static BoardPrompt typed(String words) {
        return new BoardPrompt("", "message", "Freeform message", words, "", Map.of(), List.of());
    }
}
