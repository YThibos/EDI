package io.github.ythibos.edi.domain.decision;

import io.github.ythibos.edi.domain.task.TaskId;

public class UnknownDecision extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnknownDecision(DecisionNumber number) {
        super("No decision is numbered " + number + ".");
    }

    public UnknownDecision(TaskId task) {
        super(task + " has no decision.");
    }
}
