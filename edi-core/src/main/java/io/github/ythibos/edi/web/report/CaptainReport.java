package io.github.ythibos.edi.web.report;

import io.github.ythibos.edi.application.fleet.FleetSnapshot;
import io.github.ythibos.edi.domain.presence.Presence;

public record CaptainReport(FleetSnapshot fleet, Turn turn, ReportWidth width, Presence presence) {

    public boolean showsThePresence() {
        return !presence.isPresent() || presence.demoUntil() != null;
    }
}
