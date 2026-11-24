package io.github.ythibos.edi.domain.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.task.Hold.Approval;
import io.github.ythibos.edi.domain.task.Hold.Deferral;
import io.github.ythibos.edi.domain.task.Hold.OpenDecision;
import io.github.ythibos.edi.domain.task.Hold.Outage;

class HoldsTest {

    private static final Instant AT = Instant.parse("2026-10-27T09:00:00Z");
    private static final DecisionNumber D58 = new DecisionNumber(58);
    private static final DecisionNumber D59 = new DecisionNumber(59);
    private static final Deferral AFTER_THE_SEASON = new Deferral("after the season", HoldUntil.day(LocalDate.of(2026, 11, 1)), AT);
    private static final Outage LOST_AT_THE_RESTART = new Outage(3, "lost when the machine restarted", AT);

    @Test
    void holdsSeveralOpenDecisionsAtOnce() {
        Holds holds = Holds.none().with(waitingOn(D58)).with(waitingOn(D59));

        assertThat(holds.openDecisions()).containsExactly(D58, D59);
        assertThat(holds.reasons()).containsExactly("D58 waits for its answer", "D59 waits for its answer");
    }

    @Test
    void removesOnlyTheAnsweredDecision() {
        Holds holds = Holds.none().with(waitingOn(D58)).with(waitingOn(D59));

        assertThat(holds.answered(D58).openDecisions()).containsExactly(D59);
    }

    @Test
    void keepsADeferralAcrossADecisionAndItsAnswer() {
        Holds deferred = Holds.none().with(AFTER_THE_SEASON);

        Holds afterTheAnswer = deferred.with(waitingOn(D58)).answered(D58);

        assertThat(afterTheAnswer).isEqualTo(deferred);
    }

    @Test
    void keepsAnOutageEntryBesideADecision() {
        Holds holds = Holds.none().with(LOST_AT_THE_RESTART).with(waitingOn(D58));

        assertThat(holds.reasons()).containsExactly(
                "lost when the machine restarted; number 3 on the outage hold list waits for the captain to resume it or start it fresh",
                "D58 waits for its answer");
    }

    @Test
    void refusesTheSameDecisionTwice() {
        Holds holds = Holds.none().with(waitingOn(D58));

        assertThatThrownBy(() -> holds.with(new OpenDecision(D58, AT.plusSeconds(60)))).isInstanceOf(Refusal.class)
                .hasMessage("The task already waits: D58 waits for its answer.");
    }

    @Test
    void refusesASecondDeferral() {
        Holds deferred = Holds.none().with(AFTER_THE_SEASON);

        assertThatThrownBy(() -> deferred.with(new Deferral("until the spring", HoldUntil.captainsWord(), AT))).isInstanceOf(Refusal.class)
                .hasMessage("The task already waits: deferred: after the season.");
    }

    @Test
    void refusesAnAnswerToADecisionThatDoesNotHoldTheTask() {
        assertThatThrownBy(() -> Holds.none().answered(D58)).isInstanceOf(Refusal.class)
                .hasMessage("The task does not wait: D58 waits for its answer.");
    }

    @Test
    void releasesAnOutageEntryAndLeavesTheRest() {
        Holds holds = Holds.none().with(LOST_AT_THE_RESTART).with(AFTER_THE_SEASON).with(waitingOn(D58));

        assertThat(holds.released(LOST_AT_THE_RESTART).held()).containsExactly(AFTER_THE_SEASON, waitingOn(D58));
    }

    @Test
    void releasesADecisionOnlyThroughItsAnswer() {
        Holds holds = Holds.none().with(waitingOn(D58));

        assertThatThrownBy(() -> holds.released(waitingOn(D58))).isInstanceOf(Refusal.class)
                .hasMessage("D58 holds the task until it is answered; answering it releases it.");
    }

    @Test
    void releasesEveryHoldButTheOpenDecisionsAtOnce() {
        Approval approval = new Approval("the lamp's budget", HoldUntil.captainsWord(), AT);
        Holds holds = Holds.none().with(approval).with(waitingOn(D58)).with(AFTER_THE_SEASON).with(waitingOn(D59));

        assertThat(holds.releasable()).containsExactly(approval, AFTER_THE_SEASON);
        assertThat(holds.released().held()).containsExactly(waitingOn(D58), waitingOn(D59));
        assertThat(holds.released().holdsOnlyOpenDecisions()).isTrue();
    }

    @Test
    void namesTheHoldOfEachKindItHolds() {
        Holds holds = Holds.none().with(AFTER_THE_SEASON).with(waitingOn(D58));

        assertThat(holds.ofKind(HoldKind.DEFERRAL)).contains(AFTER_THE_SEASON);
        assertThat(holds.ofKind(HoldKind.APPROVAL)).isEmpty();
    }

    @Test
    void takesOnlyADeferralOrAnApprovalFromTheCaptain() {
        assertThat(Hold.setByTheCaptain(HoldKind.DEFERRAL, "after the season", HoldUntil.day(LocalDate.of(2026, 11, 1)), AT)).isEqualTo(AFTER_THE_SEASON);
        assertThatThrownBy(() -> Hold.setByTheCaptain(HoldKind.DECISION, "decide", HoldUntil.captainsWord(), AT)).isInstanceOf(Refusal.class)
                .hasMessage("A decision holds its task when it is raised with its card.");
        assertThatThrownBy(() -> Hold.setByTheCaptain(HoldKind.OUTAGE, "lost", HoldUntil.captainsWord(), AT)).isInstanceOf(Refusal.class)
                .hasMessage("An outage entry holds its task when its worker is lost in an outage.");
    }

    @Test
    void refusesAHoldWithoutItsReason() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Deferral("  ", HoldUntil.captainsWord(), AT));
        assertThatIllegalArgumentException().isThrownBy(() -> new Approval("", HoldUntil.captainsWord(), AT));
        assertThatIllegalArgumentException().isThrownBy(() -> new Outage(0, "lost when the machine restarted", AT));
        assertThatIllegalArgumentException().isThrownBy(() -> new Outage(3, " ", AT));
    }

    private static OpenDecision waitingOn(DecisionNumber number) {
        return new OpenDecision(number, AT);
    }
}
