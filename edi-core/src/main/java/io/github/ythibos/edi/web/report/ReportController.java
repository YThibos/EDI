package io.github.ythibos.edi.web.report;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.fleet.FleetSnapshotQuery;
import io.github.ythibos.edi.application.presence.MeetingCalendar;
import io.github.ythibos.edi.application.presence.PresenceQuery;
import io.github.ythibos.edi.application.primary.PrimaryHooks;
import io.github.ythibos.edi.domain.voice.CaptainVoice;
import io.github.ythibos.edi.web.access.Caller;
import tools.jackson.databind.JsonNode;

@RestController
class ReportController {

    private final FleetSnapshotQuery fleet;
    private final PresenceQuery presence;
    private final MeetingCalendar calendar;
    private final PrimaryHooks primaryHooks;

    ReportController(FleetSnapshotQuery fleet, PresenceQuery presence, MeetingCalendar calendar, PrimaryHooks primaryHooks) {
        this.fleet = fleet;
        this.presence = presence;
        this.calendar = calendar;
        this.primaryHooks = primaryHooks;
    }

    @GetMapping("/api/report")
    CaptainReport report(@RequestParam(required = false) Integer paneWidth, Caller caller) {
        return reportOf(Turn.NONE, paneWidth, caller);
    }

    @PostMapping("/api/report")
    CaptainReport report(@RequestBody JsonNode turn, @RequestParam(required = false) Integer paneWidth, Caller caller) {
        Turn read = TurnDocument.read(turn);
        CaptainVoice.requireOutcomes("The turn's wording in the captain report", read.wording());
        return reportOf(read, paneWidth, caller);
    }

    private CaptainReport reportOf(Turn turn, Integer paneWidth, Caller caller) {
        CaptainReport report = new CaptainReport(fleet.snapshot(), turn, paneWidth == null ? ReportWidth.standard() : ReportWidth.forPane(paneWidth),
                presence.current(), calendar.unreadable().orElse(null));
        caller.claudeSession().ifPresent(primaryHooks::reportRendered);
        return report;
    }
}
