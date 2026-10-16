package io.github.ythibos.edi.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.application.delivery.DeliverDraft.DeliveredDraft;
import io.github.ythibos.edi.application.delivery.FollowMerges.CleanUp;
import io.github.ythibos.edi.application.delivery.FollowMerges.MergedWork;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;

class ConfirmationTextTest {

    private static final TaskId LANTERN = new TaskId("lantern");

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

    @Test
    void confirmsADeliveryAMergeAndACleanUp() {
        MergeRequestAddress address = new MergeRequestAddress(ForgeRepository.registered(ForgeKind.GITHUB, null, "example-owner/harness"), 7);
        MergeRequestView merged = new MergeRequestView(address, MergeRequestState.MERGED, true, "feature/HRN-3", "main", "1111", "3333");
        Delivery delivery = Delivery.of(LANTERN, new MergeRequestView(address, MergeRequestState.OPEN, true, "feature/HRN-3", "main", "1111", null),
                Instant.parse("2026-10-16T09:00:00Z"));

        assertThat(text.text(new Confirmation.DraftDelivered(new DeliveredDraft(delivery, true)))).isEqualTo(
                "Opened the draft https://github.com/example-owner/harness/pull/7 for lantern; EDI watches it until the captain merges it.\n");
        assertThat(text.text(new Confirmation.DraftDelivered(new DeliveredDraft(delivery, false)))).startsWith("Recorded the open draft ");
        assertThat(text.text(new Confirmation.MergedWorkFollowedUp(new MergedWork(LANTERN, delivery.observed(merged, Instant.parse("2026-10-16T10:00:00Z")), new CleanUp(true, null))))).isEqualTo(
                "Recorded the merge of https://github.com/example-owner/harness/pull/7 as 3333; lantern is cleaned up and done.\n");
        assertThat(text.text(new Confirmation.MergedWorkFollowedUp(new MergedWork(LANTERN, null, new CleanUp(false, "a commit is not landed")))))
                .isEqualTo("The captain merged lantern locally; lantern is not cleaned up yet: a commit is not landed\n");
        assertThat(text.text(new Confirmation.CleanedUp(PresenterFixtures.lantern()))).isEqualTo(
                "Cleaned up lantern: its work is landed, its isolated copy and branch are removed, and the task is done.\n");
    }
}
