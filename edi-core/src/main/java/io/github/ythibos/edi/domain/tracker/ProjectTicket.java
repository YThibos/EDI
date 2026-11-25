package io.github.ythibos.edi.domain.tracker;

import java.util.Objects;

public record ProjectTicket(Ticket ticket, String issueType) {

    public ProjectTicket {
        Objects.requireNonNull(ticket, "A project ticket is a ticket.");
        issueType = Objects.requireNonNullElse(issueType, "").strip();
    }

    public boolean isOfType(String type) {
        return issueType.equalsIgnoreCase(type);
    }
}
