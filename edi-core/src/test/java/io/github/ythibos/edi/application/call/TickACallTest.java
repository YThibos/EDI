package io.github.ythibos.edi.application.call;

import static io.github.ythibos.edi.testing.Cards.card;
import static io.github.ythibos.edi.testing.Intakes.captainItem;
import static io.github.ythibos.edi.testing.Intakes.ship;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.store.LoggedEvent;
import io.github.ythibos.edi.application.decision.GivenAnswer;
import io.github.ythibos.edi.application.fleet.DealtWith;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.call.CallKey;
import io.github.ythibos.edi.domain.call.UnknownCall;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.HeadChecks;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.testing.BoardRounds;
import io.github.ythibos.edi.testing.Intakes;
import io.github.ythibos.edi.testing.TestHome;

@Tag("adapter")
class TickACallTest {

    private static final CallKey D58 = CallKey.parse("D58");
    private static final TaskId LAMP = new TaskId("fit-lamp");
    private static final BaseBranch MAIN = new BaseBranch("main");
    private static final MergeRequestAddress REQUEST =
            new MergeRequestAddress(ForgeRepository.registered(ForgeKind.GITHUB, null, "example-owner/harness"), 7);

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void openTheHome() throws IOException {
        home = TestHome.in(directory).registering(Intakes.REGISTRY).continuingDecisionsFrom(57);
        home.addTask().add(captainItem("lamp-choice", "Choose the lamp"), Actor.PRIMARY);
        home.raiseDecision().raise(new TaskReference.ById(new TaskId("lamp-choice")), card("Which lamp?"), Actor.PRIMARY);
    }

    @Test
    void keepsAnAnsweredDecisionDealtWithUntilItIsTickedAndKeepsTheTickAcrossARestart() throws IOException {
        answerTheLampDecision();
        home.clock().advance(Duration.ofDays(30));

        DealtWith untilTicked = home.fleetSnapshot().snapshot().dealtWith();
        home.tickACall().tick(D58, Actor.CAPTAIN);
        DealtWith ticked = home.fleetSnapshot().snapshot().dealtWith();
        DealtWith afterARestart = home.restarted().fleetSnapshot().snapshot().dealtWith();

        assertThat(untilTicked.decisions()).extracting(answered -> answered.decision().number()).containsExactly(new DecisionNumber(58));
        assertThat(ticked.isEmpty()).isTrue();
        assertThat(afterARestart.isEmpty()).isTrue();
        assertThat(home.events().ofTask(new TaskId("lamp-choice"))).extracting(LoggedEvent::type).contains("CallTicked");
    }

    @Test
    void refusesToTickADecisionThatStillWaitsOnTheCaptain() {
        assertThatThrownBy(() -> home.tickACall().tick(D58, Actor.PRIMARY)).isInstanceOf(Refusal.class)
                .satisfies(refused -> assertThat(((Refusal) refused).rule()).isEqualTo(Rule.TICKED_WHEN_DEALT_WITH))
                .hasMessageContaining("D58 still waits on the captain");
        assertThat(home.fleetSnapshot().snapshot().waiting().decisions()).hasSize(1);
    }

    @Test
    void keepsAMergedOrClosedRequestDealtWithUntilItIsTicked() {
        home.addTask().add(ship("fit-lamp", "Fit the lamp"), Actor.PRIMARY);
        home.deliveries().save(Delivery.of(LAMP, request(MergeRequestState.OPEN), MAIN, TestHome.START));
        CallKey review = CallKey.parse("review:fit-lamp");

        assertThatThrownBy(() -> home.tickACall().tick(review, Actor.CAPTAIN)).isInstanceOf(Refusal.class);

        home.deliveries().save(home.deliveries().of(LAMP).orElseThrow().observed(request(MergeRequestState.MERGED), MAIN, TestHome.START));
        List<DealtWith.SettledRequest> merged = home.fleetSnapshot().snapshot().dealtWith().reviews();
        home.tickACall().tick(review, Actor.PRIMARY);

        assertThat(merged).extracting(settled -> settled.task().id()).containsExactly(LAMP);
        assertThat(home.fleetSnapshot().snapshot().dealtWith().reviews()).isEmpty();
    }

    @Test
    void keepsAnEndedBoardDealtWithUntilItIsTicked() {
        home.addTask().add(ship("lantern", "Rework the lantern"), Actor.PRIMARY);
        Board armed = home.boards().arm(new TaskId("lantern"), directory.resolve("board.html").toString(), BoardRounds.asking(2), TestHome.START);
        CallKey board = CallKey.parse("board:" + armed.id());

        home.boards().update(armed.ended(TestHome.START));
        List<DealtWith.EndedBoard> ended = home.fleetSnapshot().snapshot().dealtWith().boards();
        home.tickACall().tick(board, Actor.CAPTAIN);

        assertThat(ended).extracting(endedBoard -> endedBoard.board().id()).containsExactly(armed.id());
        assertThat(home.fleetSnapshot().snapshot().dealtWith().boards()).isEmpty();
    }

    @Test
    void knowsNoCallThatIsNotOnTheList() {
        assertThatThrownBy(() -> home.tickACall().tick(CallKey.parse("board:41"), Actor.CAPTAIN)).isInstanceOf(UnknownCall.class);
        assertThatThrownBy(() -> home.tickACall().tick(CallKey.parse("review:fit-lamp"), Actor.CAPTAIN)).isInstanceOf(UnknownCall.class);
    }

    private void answerTheLampDecision() {
        home.answerDecision().answer(new TaskReference.ByDecision(new DecisionNumber(58)),
                new GivenAnswer("A: the simple way", new OptionKey('A'), "", AnswerChannel.CONSOLE), Actor.CAPTAIN);
    }

    private static MergeRequestView request(MergeRequestState state) {
        boolean merged = state == MergeRequestState.MERGED;
        return new MergeRequestView(REQUEST, state, false, "feature/HRN-3", "main", "abc123", HeadChecks.PASSED, merged ? "def456" : null,
                merged ? TestHome.START : null);
    }
}
