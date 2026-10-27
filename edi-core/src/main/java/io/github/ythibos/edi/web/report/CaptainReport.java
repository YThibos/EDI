package io.github.ythibos.edi.web.report;

import io.github.ythibos.edi.application.fleet.FleetSnapshot;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.presence.UnreadableCalendar;
import io.github.ythibos.edi.domain.worker.HeavyRuns;

public record CaptainReport(FleetSnapshot fleet, Turn turn, ReportWidth width, Presence presence, UnreadableCalendar unreadableCalendar,
        HeavyRuns heavyRunsAtOnce) {

    public boolean showsThePresence() {
        return !presence.isPresent() || presence.demoUntil() != null;
    }

    public boolean showsTheUnreadableCalendar() {
        return unreadableCalendar != null;
    }

    public boolean showsTheHeavyRunsAtOnce() {
        return heavyRunsAtOnce != null;
    }
}
