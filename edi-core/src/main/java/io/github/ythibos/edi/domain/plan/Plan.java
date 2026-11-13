package io.github.ythibos.edi.domain.plan;

import java.util.List;

import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.TaskId;

public record Plan(PlanHeading heading, List<Slice> slices) {

    public Plan {
        slices = List.copyOf(slices);
    }

    public WorkKey key() {
        return heading.key();
    }

    public List<TaskId> tasks() {
        return slices.stream().map(Slice::task).toList();
    }
}
