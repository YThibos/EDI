package io.github.ythibos.edi.domain.plan;

import java.util.List;
import java.util.Optional;

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

    public Optional<TaskId> taskAwaitedBy(Slice slice) {
        return switch (slice.awaits()) {
            case SliceWait.None _ -> Optional.empty();
            case SliceWait.After after -> taskOfSlice(after.slice());
        };
    }

    private Optional<TaskId> taskOfSlice(int number) {
        return slices.stream().filter(slice -> slice.number() == number).map(Slice::task).findFirst();
    }
}
