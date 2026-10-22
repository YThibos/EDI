package io.github.ythibos.edi.web.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.application.decision.DecisionList;
import io.github.ythibos.edi.testing.ManualClock;
import io.github.ythibos.edi.web.PresenterFixtures;
import io.github.ythibos.edi.web.text.Moments;

class DecisionListTextTest {

    private final DecisionListText text = new DecisionListText(PresenterFixtures.decisionLines());

    @Test
    void showsEachDecisionWithItsCardAndAnswer() {
        assertThat(text.text(new DecisionList(false, List.of(PresenterFixtures.answeredDecision())))).isEqualTo("""
                Decisions (1)
                D58  lamp-choice  Which lamp?
                  Both work; one is simpler.
                  (A) The simple way
                  (B) The thorough way
                  Recommended: A - it is enough
                  Answered 2026-10-15 09:40 UTC (option A, chat):
                    D58, A
                    and keep the old one
                  Interpretation: The simple lamp.
                """);
        assertThat(text.text(new DecisionList(true, List.of()))).isEqualTo("Open decisions (0)\n");
    }

    @Test
    void showsWhenTheCaptainAnsweredInTheMachinesTimeZone() {
        DecisionListText inBrussels = new DecisionListText(new DecisionLines(new Moments(new ManualClock(PresenterFixtures.AT, ZoneId.of("Europe/Brussels")))));

        assertThat(inBrussels.text(new DecisionList(false, List.of(PresenterFixtures.answeredDecision()))))
                .contains("  Answered 2026-10-15 11:40 CEST (option A, chat):");
    }
}
