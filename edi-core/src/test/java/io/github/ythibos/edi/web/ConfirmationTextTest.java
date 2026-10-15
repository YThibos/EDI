package io.github.ythibos.edi.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;

class ConfirmationTextTest {

    private final ConfirmationText text = new ConfirmationText();

    @Test
    void confirmsEachChangeInOneLine() {
        assertThat(text.text(new Confirmation.Queued(new TaskId("lantern")))).isEqualTo("Queued lantern.\n");
        assertThat(text.text(new Confirmation.Noted(new TaskId("lantern")))).isEqualTo("Noted on lantern.\n");
        assertThat(text.text(new Confirmation.Held(PresenterFixtures.heldHorn())))
                .isEqualTo("Held fog-horn (deferral, until 2026-11-01): after the storm\n");
        assertThat(text.text(new Confirmation.Released(PresenterFixtures.lantern()))).isEqualTo("Released lantern; it is queued again.\n");
        assertThat(text.text(new Confirmation.Raised(new DecisionNumber(58)))).isEqualTo("D58\n");
    }

    @Test
    void confirmsAnAnswerAndWhatBecameOfItsTask() {
        Task closed = PresenterFixtures.captainItem()
                .heldForDecision(PresenterFixtures.openDecision().holdOnItsTask())
                .decisionAnswered(Instant.parse("2026-10-15T10:00:00Z"));

        assertThat(text.text(new Confirmation.Answered(PresenterFixtures.answeredDecision(), closed)))
                .isEqualTo("Recorded the answer to D58 (option A); lamp-choice is done.\n");
    }
}
