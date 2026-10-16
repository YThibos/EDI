package io.github.ythibos.edi.domain.event;

import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.task.CaptainHold;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.TaskId;

public sealed interface DomainEvent {

    TaskId task();

    record TaskAdded(TaskId task, Intake intake) implements DomainEvent {
    }

    record TaskNoted(TaskId task, String note) implements DomainEvent {
    }

    record TaskHeld(TaskId task, CaptainHold hold) implements DomainEvent {
    }

    record TaskReleased(TaskId task) implements DomainEvent {
    }

    record DecisionRaised(TaskId task, DecisionNumber number, DecisionCard card) implements DomainEvent {
    }

    record DecisionAnswered(TaskId task, DecisionNumber number, CaptainAnswer answer) implements DomainEvent {
    }

    record TaskDone(TaskId task) implements DomainEvent {
    }
}
