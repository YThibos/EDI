package io.github.ythibos.edi.domain.tracker;

import java.util.Objects;

import io.github.ythibos.edi.domain.project.ProjectKey;

public record TicketContext(ProjectKey project, String text) {

    public TicketContext {
        Objects.requireNonNull(project, "A ticket is looked for in one tracker project.");
        text = Objects.requireNonNullElse(text, "").strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("A ticket is looked for by the words of the work.");
        }
    }
}
