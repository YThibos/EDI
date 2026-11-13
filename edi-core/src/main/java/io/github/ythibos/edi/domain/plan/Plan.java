package io.github.ythibos.edi.domain.plan;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.Wait;

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

    public List<SliceStanding> standings(Map<TaskId, Task> tasks) {
        return slices.stream().map(slice -> new SliceStanding(slice, tasks.get(slice.task()), waitOf(slice, tasks))).toList();
    }

    private Optional<Wait> waitOf(Slice slice, Map<TaskId, Task> tasks) {
        return switch (slice.awaits()) {
            case SliceWait.None _ -> Optional.empty();
            case SliceWait.After after -> Optional.of(waitFor(tasks.get(sliceNumbered(after.slice()).task()), after));
        };
    }

    private Slice sliceNumbered(int number) {
        return slices.stream()
                .filter(slice -> slice.number() == number)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The plan " + key() + " has no slice " + number + "."));
    }

    private static Wait waitFor(Task awaited, SliceWait.After after) {
        return new Wait(awaited.id(), after.stage(), awaited.stage());
    }
}
