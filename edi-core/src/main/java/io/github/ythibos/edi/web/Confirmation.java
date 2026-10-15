package io.github.ythibos.edi.web;

import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;

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
}
