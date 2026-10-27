package io.github.ythibos.edi.domain.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.task.Hold.Approval;
import io.github.ythibos.edi.domain.task.Hold.Deferral;
import io.github.ythibos.edi.domain.task.Hold.OpenDecision;
import io.github.ythibos.edi.domain.task.Hold.Outage;

class HoldsTest {

    private static final DecisionNumber D58 = new DecisionNumber(58);
    private static final DecisionNumber D59 = new DecisionNumber(59);
    private static final Deferral AFTER_THE_SEASON = new Deferral("after the season", HoldUntil.day(LocalDate.of(2026, 11, 1)));

    @Test
    void holdsSeveralOpenDecisionsAtOnce() {
        Holds holds = Holds.none().with(new OpenDecision(D58)).with(new OpenDecision(D59));

        assertThat(holds.openDecisions()).containsExactly(D58, D59);
        assertThat(holds.reasons()).containsExactly("D58 waits for its answer", "D59 waits for its answer");
    }

    @Test
    void removesOnlyTheAnsweredDecision() {
        Holds holds = Holds.none().with(new OpenDecision(D58)).with(new OpenDecision(D59));

        assertThat(holds.answered(D58).openDecisions()).containsExactly(D59);
    }

    @Test
    void keepsADeferralAcrossADecisionAndItsAnswer() {
        Holds deferred = Holds.none().with(AFTER_THE_SEASON);

        Holds afterTheAnswer = deferred.with(new OpenDecision(D58)).answered(D58);

        assertThat(afterTheAnswer).isEqualTo(deferred);
    }

    @Test
    void keepsAnOutageEntryBesideADecision() {
        Holds holds = Holds.none().with(new Outage(3)).with(new OpenDecision(D58));

        assertThat(holds.reasons()).containsExactly("number 3 on the outage hold list", "D58 waits for its answer");
    }

    @Test
    void refusesTheSameDecisionTwice() {
        Holds holds = Holds.none().with(new OpenDecision(D58));

        assertThatThrownBy(() -> holds.with(new OpenDecision(D58))).isInstanceOf(Refusal.class)
                .hasMessage("The task already waits: D58 waits for its answer.");
    }

    @Test
    void refusesASecondDeferral() {
        Holds deferred = Holds.none().with(AFTER_THE_SEASON);

        assertThatThrownBy(() -> deferred.with(new Deferral("until the spring", HoldUntil.captainsWord()))).isInstanceOf(Refusal.class)
                .hasMessage("The task already waits: deferred: after the season.");
    }

    @Test
    void refusesAnAnswerToADecisionThatDoesNotHoldTheTask() {
        assertThatThrownBy(() -> Holds.none().answered(D58)).isInstanceOf(Refusal.class)
                .hasMessage("The task does not wait: D58 waits for its answer.");
    }

    @Test
    void releasesAnApprovalAndLeavesTheRest() {
        Approval approval = new Approval("the lamp's budget");
        Holds holds = Holds.none().with(approval).with(new OpenDecision(D58));

        assertThat(holds.released(approval).openDecisions()).containsExactly(D58);
        assertThat(holds.released(approval).reasons()).containsExactly("D58 waits for its answer");
    }

    @Test
    void releasesADecisionOnlyThroughItsAnswer() {
        Holds holds = Holds.none().with(new OpenDecision(D58));

        assertThatThrownBy(() -> holds.released(new OpenDecision(D58))).isInstanceOf(Refusal.class)
                .hasMessage("D58 holds the task until it is answered; answering it releases it.");
    }

    @Test
    void refusesAHoldWithoutItsReason() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Deferral("  ", HoldUntil.captainsWord()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Approval(""));
        assertThatIllegalArgumentException().isThrownBy(() -> new Outage(0));
    }
}
