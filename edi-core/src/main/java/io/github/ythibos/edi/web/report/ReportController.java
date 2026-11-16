package io.github.ythibos.edi.web.report;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.fleet.FleetSnapshotQuery;
import io.github.ythibos.edi.application.presence.MeetingCalendar;
import io.github.ythibos.edi.application.presence.PresenceQuery;
import io.github.ythibos.edi.application.worker.NoticeHeavyRunsAtOnce;
import io.github.ythibos.edi.web.access.Caller;

@RestController
class ReportController {

    private final FleetSnapshotQuery fleet;
    private final PresenceQuery presence;
    private final MeetingCalendar calendar;
    private final NoticeHeavyRunsAtOnce heavyRuns;
    private final TurnReports turnReports;

    ReportController(FleetSnapshotQuery fleet, PresenceQuery presence, MeetingCalendar calendar, NoticeHeavyRunsAtOnce heavyRuns, TurnReports turnReports) {
        this.fleet = fleet;
        this.presence = presence;
        this.calendar = calendar;
        this.heavyRuns = heavyRuns;
        this.turnReports = turnReports;
    }

    @GetMapping("/api/report")
    CaptainReport sitrep(@RequestParam(required = false) Integer paneWidth, Caller caller) {
        CaptainReport sitrep = new CaptainReport(fleet.snapshot(), turnReports.itemsRecordedIn(caller.claudeSession()), ReportWidth.forPaneIfKnown(paneWidth),
                presence.current(), calendar.unreadable().orElse(null), heavyRuns.atOnce().orElse(null));
        turnReports.recordTheReportShownIn(caller.claudeSession());
        return sitrep;
    }

    @PostMapping("/api/report")
    TurnReport turnReport(@RequestParam(required = false) Integer paneWidth, Caller caller) {
        return turnReports.shownIn(caller.claudeSession(), paneWidth);
    }
}
