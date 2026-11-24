package io.github.ythibos.edi.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.decision.DecisionNumber;

class PlanGoTest {

    @Test
    void readsAGoWrittenAsADecisionNumberAsThatDecisionAndAnyOtherWordsAsUncited() {
        assertThat(PlanGo.named(" D44 ")).isEqualTo(new PlanGo.AnsweredDecision(new DecisionNumber(44)));
        assertThat(PlanGo.named("Q13 A, in chat")).isEqualTo(new PlanGo.Uncited("Q13 A, in chat"));
        assertThat(PlanGo.named("D44 and D45")).isEqualTo(new PlanGo.Uncited("D44 and D45"));
    }
}
