package io.github.ythibos.edi.domain.plan;

import java.util.Objects;

import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.task.TaskId;

public record Slice(int number, TaskId task, CategoryId category, SliceWait awaits) {

    public Slice {
        if (number < 1) {
            throw new IllegalArgumentException("A plan numbers its slices from 1: " + number);
        }
        Objects.requireNonNull(task, "A slice is a task.");
        Objects.requireNonNull(category, "A slice names its dispatch category.");
        Objects.requireNonNull(awaits, "A slice names its wait, or none.");
    }
}
