package io.github.ythibos.edi.domain.dispatch;

import io.github.ythibos.edi.domain.task.TaskId;

public record CategoryId(String value) {

    public static final CategoryId RESEARCH = new CategoryId("research");
    public static final CategoryId IMPLEMENTATION = new CategoryId("implementation");
    public static final CategoryId MECHANICAL = new CategoryId("mechanical");

    public CategoryId {
        if (!TaskId.isWritten(value)) {
            throw new IllegalArgumentException("A dispatch category is a kebab-case id, such as implementation: " + value);
        }
    }

    public boolean isMechanical() {
        return equals(MECHANICAL);
    }

    @Override
    public String toString() {
        return value;
    }
}
