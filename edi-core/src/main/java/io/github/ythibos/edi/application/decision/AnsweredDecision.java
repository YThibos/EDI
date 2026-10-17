package io.github.ythibos.edi.application.decision;

import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.tracker.TicketSettlement;

public record AnsweredDecision(Decision decision, Task task, TicketSettlement ticket) {

    AnsweredDecision withTicket(TicketSettlement settlement) {
        return new AnsweredDecision(decision, task, settlement);
    }
}
