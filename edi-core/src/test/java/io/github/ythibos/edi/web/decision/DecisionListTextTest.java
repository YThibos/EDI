package io.github.ythibos.edi.web.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.application.decision.DecisionList;
import io.github.ythibos.edi.web.PresenterFixtures;

class DecisionListTextTest {

    @Test
    void showsEachDecisionWithItsCardAndAnswer() {
        assertThat(new DecisionListText().text(new DecisionList(false, List.of(PresenterFixtures.answeredDecision())))).isEqualTo("""
                Decisions (1)
                D58  lamp-choice  Which lamp?
                  Both work; one is simpler.
                  (A) The simple way
                  (B) The thorough way
                  Recommended: A - it is enough
                  Answered 2026-10-15 09:40Z (option A, chat):
                    D58, A
                    and keep the old one
                  Interpretation: The simple lamp.
                """);
        assertThat(new DecisionListText().text(new DecisionList(true, List.of()))).isEqualTo("Open decisions (0)\n");
    }
}
