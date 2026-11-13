package io.github.ythibos.edi.domain.plan;

import java.util.Objects;

import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.TaskId;

public record SliceDraft(int number, String title, CategoryId category, WorkKey key, String body, SliceWait awaits) {

    public SliceDraft {
        if (number < 1) {
            throw new IllegalArgumentException("A plan numbers its slices from 1: " + number);
        }
        title = Objects.requireNonNullElse(title, "").strip();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("Slice " + number + " has a title.");
        }
        Objects.requireNonNull(category, "Slice " + number + " names its dispatch category.");
        Objects.requireNonNull(key, "Slice " + number + " has its own work key, because tasks that share a key share a branch.");
        body = Objects.requireNonNullElse(body, "");
    }

    public boolean namesItsWait() {
        return awaits != null;
    }

    public SliceDraft waitingFor(SliceWait chosen) {
        return new SliceDraft(number, title, category, key, body, chosen);
    }

    public Slice plannedAs(TaskId task) {
        return new Slice(number, task, category, awaits);
    }
}
