package io.github.ythibos.edi.web;

import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.application.worker.SteerWorker;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;

public sealed interface Confirmation {

    record Queued(TaskId task) implements Confirmation {
    }

    record Noted(TaskId task) implements Confirmation {
    }

    record Held(Task task) implements Confirmation {
    }

    record Released(Task task) implements Confirmation {
    }

    record Raised(DecisionNumber number) implements Confirmation {
    }

    record Answered(Decision decision, Task task) implements Confirmation {
    }

    record Dispatched(DispatchOutcome.Launched launched) implements Confirmation {
    }

    record Deferred(DispatchOutcome.Deferred deferred) implements Confirmation {
    }

    record StatusRecorded(Worker worker) implements Confirmation {
    }

    record Acknowledged(InboxMessage message) implements Confirmation {
    }

    record Steered(SteerWorker.Steered steered) implements Confirmation {
    }
}
