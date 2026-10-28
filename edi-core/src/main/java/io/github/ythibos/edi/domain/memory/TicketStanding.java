package io.github.ythibos.edi.domain.memory;

import java.util.Map;

import io.github.ythibos.edi.domain.project.WorkKey;

public enum TicketStanding {
    OPEN,
    LANDED,
    UNTICKETED,
    UNCHECKED;

    public static TicketStanding of(WorkKey key, Map<WorkKey, TicketStanding> tickets) {
        return tickets.getOrDefault(key, UNCHECKED);
    }
}
