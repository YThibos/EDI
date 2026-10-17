package io.github.ythibos.edi.domain.tracker;

public sealed interface WorkflowStep {

    record Take(AvailableTransition transition) implements WorkflowStep {
    }

    record Arrived() implements WorkflowStep {
    }

    record AlreadyBeyond(String status) implements WorkflowStep {
    }

    record Stuck(String because) implements WorkflowStep {
    }
}
