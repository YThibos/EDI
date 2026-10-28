package io.github.ythibos.edi.web.report;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.fleet.FleetSnapshotQuery;
import io.github.ythibos.edi.application.presence.MeetingCalendar;
import io.github.ythibos.edi.application.presence.PresenceQuery;
import io.github.ythibos.edi.application.primary.PrimaryHooks;
import io.github.ythibos.edi.application.primary.PrimarySessions;
import io.github.ythibos.edi.application.worker.NoticeHeavyRunsAtOnce;
import io.github.ythibos.edi.domain.voice.CaptainVoice;
import io.github.ythibos.edi.web.access.Caller;
import tools.jackson.databind.JsonNode;

@RestController
class ReportController {

    private final FleetSnapshotQuery fleet;
    private final PresenceQuery presence;
    private final MeetingCalendar calendar;
    private final PrimarySessions sessions;
    private final PrimaryHooks primaryHooks;
    private final NoticeHeavyRunsAtOnce heavyRuns;

    ReportController(FleetSnapshotQuery fleet, PresenceQuery presence, MeetingCalendar calendar, PrimarySessions sessions, PrimaryHooks primaryHooks,
            NoticeHeavyRunsAtOnce heavyRuns) {
        this.fleet = fleet;
        this.presence = presence;
        this.calendar = calendar;
        this.sessions = sessions;
        this.primaryHooks = primaryHooks;
        this.heavyRuns = heavyRuns;
    }

    @GetMapping("/api/report")
    CaptainReport sitrep(@RequestParam(required = false) Integer paneWidth, Caller caller) {
        CaptainReport sitrep = new CaptainReport(fleet.snapshot(), Turn.NONE, widthFor(paneWidth), presence.current(), calendar.unreadable().orElse(null),
                heavyRuns.atOnce().orElse(null));
        recordTheReportShownTo(caller);
        return sitrep;
    }

    @PostMapping("/api/report")
    TurnReport turnReport(@RequestBody JsonNode turn, @RequestParam(required = false) Integer paneWidth, Caller caller) {
        Turn read = TurnDocument.read(turn);
        CaptainVoice.requireOutcomes("The turn's wording in the captain report", read.wording());
        TurnReport report = new TurnReport(fleet.snapshot().waiting(), read, widthFor(paneWidth), calendar.unreadable().orElse(null),
                previousReportShownTo(caller));
        recordTheReportShownTo(caller);
        return report;
    }

    private static ReportWidth widthFor(Integer paneWidth) {
        return paneWidth == null ? ReportWidth.standard() : ReportWidth.forPane(paneWidth);
    }

    private void recordTheReportShownTo(Caller caller) {
        caller.claudeSession().ifPresent(primaryHooks::reportRendered);
    }

    private Instant previousReportShownTo(Caller caller) {
        return caller.claudeSession().flatMap(sessions::previousReportOf).orElse(null);
    }
}
