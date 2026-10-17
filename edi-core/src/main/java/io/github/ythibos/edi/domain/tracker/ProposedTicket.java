package io.github.ythibos.edi.domain.tracker;

import java.time.Instant;
import java.util.Objects;

import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.TaskId;

public record ProposedTicket(
        TicketProposal proposal,
        Instant proposedAt,
        Instant createTriedAt,
        WorkKey createdKey,
        Instant filledAt,
        WorkKey settledKey,
        Instant settledAt,
        String refusal) {

    public ProposedTicket {
        Objects.requireNonNull(proposal, "A proposed ticket carries its proposal.");
        Objects.requireNonNull(proposedAt, () -> "The ticket proposal for " + proposal.task() + " records when it was made.");
        if ((settledKey == null) != (settledAt == null)) {
            throw new IllegalArgumentException("The ticket proposal for " + proposal.task() + " is settled on a key at a time, or not at all.");
        }
        if (filledAt != null && createdKey == null) {
            throw new IllegalArgumentException("The ticket proposal for " + proposal.task() + " fills only a ticket it created.");
        }
    }

    public static ProposedTicket proposed(TicketProposal proposal, Instant at) {
        return new ProposedTicket(proposal, at, null, null, null, null, null, null);
    }

    public TaskId task() {
        return proposal.task();
    }

    public boolean isSettled() {
        return settledAt != null;
    }

    public boolean triedToCreate() {
        return createTriedAt != null;
    }

    public boolean hasCreated() {
        return createdKey != null;
    }

    public boolean isFilled() {
        return filledAt != null;
    }

    public ProposedTicket createTried(Instant at) {
        return new ProposedTicket(proposal, proposedAt, at, createdKey, filledAt, settledKey, settledAt, refusal);
    }

    public ProposedTicket created(WorkKey key) {
        return new ProposedTicket(proposal, proposedAt, createTriedAt, key, filledAt, settledKey, settledAt, refusal);
    }

    public ProposedTicket filled(Instant at) {
        return new ProposedTicket(proposal, proposedAt, createTriedAt, createdKey, at, settledKey, settledAt, refusal);
    }

    public ProposedTicket refusedBecause(String reason) {
        return new ProposedTicket(proposal, proposedAt, createTriedAt, createdKey, filledAt, settledKey, settledAt, reason);
    }

    public ProposedTicket settledOn(WorkKey key, Instant at) {
        return new ProposedTicket(proposal, proposedAt, createTriedAt, createdKey, filledAt, key, at, null);
    }
}
