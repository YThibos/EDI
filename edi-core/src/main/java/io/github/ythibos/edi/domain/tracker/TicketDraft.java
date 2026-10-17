package io.github.ythibos.edi.domain.tracker;

import java.util.List;
import java.util.Objects;

import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.WorkKey;

public record TicketDraft(
        ProjectKey project,
        String issueType,
        String summary,
        String description,
        WorkKey epic,
        List<String> labels,
        String priority) {

    public TicketDraft {
        Objects.requireNonNull(project, "A new ticket belongs to one tracker project.");
        issueType = Objects.requireNonNullElse(issueType, "").strip();
        summary = Objects.requireNonNullElse(summary, "").strip();
        description = Objects.requireNonNullElse(description, "").strip();
        labels = List.copyOf(labels);
        priority = Objects.requireNonNullElse(priority, "").strip();
        if (summary.isEmpty()) {
            throw new IllegalArgumentException("A new ticket has a summary.");
        }
        labels.stream().filter(label -> label.isBlank() || label.chars().anyMatch(Character::isWhitespace)).findFirst().ifPresent(label -> {
            throw new IllegalArgumentException("A ticket label is one word without spaces, not \"" + label + "\".");
        });
    }

    public boolean namesAnEpic() {
        return epic != null;
    }

    public boolean namesAPriority() {
        return !priority.isEmpty();
    }

    public String described() {
        StringBuilder text = new StringBuilder(issueType.isEmpty() ? "a ticket" : "a " + issueType).append(" \"").append(summary).append('"');
        if (namesAnEpic()) {
            text.append(" in epic ").append(epic);
        }
        if (!labels.isEmpty()) {
            text.append(", labelled ").append(String.join(", ", labels));
        }
        if (namesAPriority()) {
            text.append(", priority ").append(priority);
        }
        return text.toString();
    }
}
