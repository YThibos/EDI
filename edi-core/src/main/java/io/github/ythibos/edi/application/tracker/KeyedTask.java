package io.github.ythibos.edi.application.tracker;

import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.task.Task;

public record KeyedTask(Task task, DecisionNumber closedDecision) {

    public boolean closedItsTicketDecision() {
        return closedDecision != null;
    }
}
