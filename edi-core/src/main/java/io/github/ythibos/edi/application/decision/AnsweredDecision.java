package io.github.ythibos.edi.application.decision;

import java.util.Optional;

import io.github.ythibos.edi.application.worker.SteerWorker.Steered;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.tracker.TicketSettlement;

public record AnsweredDecision(Decision decision, Task task, TicketSettlement ticket, Optional<Steered> carriedToItsWorker) {
}
