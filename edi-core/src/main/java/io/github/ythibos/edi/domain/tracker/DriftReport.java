package io.github.ythibos.edi.domain.tracker;

import java.util.List;

public record DriftReport(String project, List<TicketDrift> drifts, List<String> unchecked) {

    public DriftReport {
        drifts = List.copyOf(drifts);
        unchecked = List.copyOf(unchecked);
    }
}
