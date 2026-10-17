package io.github.ythibos.edi.domain.tracker;

import java.util.List;
import java.util.Objects;

import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.WorkKey;

public record TicketDetails(String issueType, WorkKey epic, List<String> labels, String priority) {

    public static final TicketDetails NONE = new TicketDetails(null, null, List.of(), null);

    public TicketDetails {
        issueType = Objects.requireNonNullElse(issueType, "").strip();
        labels = List.copyOf(Objects.requireNonNullElse(labels, List.of()));
        priority = Objects.requireNonNullElse(priority, "").strip();
    }

    public boolean isEmpty() {
        return issueType.isEmpty() && epic == null && labels.isEmpty() && priority.isEmpty();
    }

    public TicketDraft draftFor(ProjectKey project, String usualIssueType, String summary, String description) {
        return new TicketDraft(project, issueType.isEmpty() ? usualIssueType : issueType, summary, description, epic, labels, priority);
    }
}
