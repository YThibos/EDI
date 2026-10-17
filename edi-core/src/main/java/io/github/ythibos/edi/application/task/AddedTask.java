package io.github.ythibos.edi.application.task;

import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.tracker.Ticket;

public record AddedTask(TaskId id, Ticket ticket, DecisionNumber ticketDecision) {

    public boolean awaitsItsTicket() {
        return ticketDecision != null;
    }
}
