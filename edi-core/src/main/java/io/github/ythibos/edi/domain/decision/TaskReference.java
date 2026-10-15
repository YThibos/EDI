package io.github.ythibos.edi.domain.decision;

import io.github.ythibos.edi.domain.task.TaskId;

public sealed interface TaskReference {

    static TaskReference parse(String text) {
        return DecisionNumber.isWritten(text) ? new ByDecision(DecisionNumber.parse(text)) : new ById(new TaskId(text));
    }

    record ById(TaskId id) implements TaskReference {
    }

    record ByDecision(DecisionNumber number) implements TaskReference {
    }
}
