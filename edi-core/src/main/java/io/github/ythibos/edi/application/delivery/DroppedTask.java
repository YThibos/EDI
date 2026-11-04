package io.github.ythibos.edi.application.delivery;

import java.util.List;

import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.task.Task;

public record DroppedTask(Task task, List<String> discarded, DecisionNumber closedDecision) {

    public DroppedTask {
        discarded = List.copyOf(discarded);
    }

    public boolean closedItsTicketDecision() {
        return closedDecision != null;
    }
}
