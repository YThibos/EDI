package io.github.ythibos.edi.domain.worker;

import java.util.Objects;

import io.github.ythibos.edi.domain.AsciiCharacters;
import io.github.ythibos.edi.domain.task.TaskId;

public record WorkerId(TaskId task, int incarnation) {

    private static final char SEPARATOR = '#';
    private static final int MOST_DIGITS = 6;

    public WorkerId {
        Objects.requireNonNull(task, "A worker works on a task.");
        if (incarnation < 1) {
            throw new IllegalArgumentException("A task's workers are numbered from 1: " + task + SEPARATOR + incarnation);
        }
    }

    public static WorkerId first(TaskId task) {
        return new WorkerId(task, 1);
    }

    public static WorkerId parse(String text) {
        int separator = text.lastIndexOf(SEPARATOR);
        String digits = text.substring(separator + 1);
        if (separator < 1 || digits.length() > MOST_DIGITS || !AsciiCharacters.isDigits(digits)) {
            throw new IllegalArgumentException("A worker is its task and a number, such as fix-login-timeout#1: " + text);
        }
        return new WorkerId(new TaskId(text.substring(0, separator)), Integer.parseInt(digits));
    }

    public WorkerId next() {
        return new WorkerId(task, incarnation + 1);
    }

    @Override
    public String toString() {
        return task.value() + SEPARATOR + incarnation;
    }
}
