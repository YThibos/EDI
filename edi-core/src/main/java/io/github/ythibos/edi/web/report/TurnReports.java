package io.github.ythibos.edi.web.report;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.decision.DecisionRoundQuery;
import io.github.ythibos.edi.application.fleet.FleetSnapshotQuery;
import io.github.ythibos.edi.application.presence.MeetingCalendar;
import io.github.ythibos.edi.application.primary.PrimaryHooks;
import io.github.ythibos.edi.application.primary.PrimarySessions;
import io.github.ythibos.edi.application.primary.TurnItemsQuery;
import io.github.ythibos.edi.domain.report.Turn;

@Component
public class TurnReports {

    private final FleetSnapshotQuery fleet;
    private final MeetingCalendar calendar;
    private final PrimarySessions sessions;
    private final PrimaryHooks primaryHooks;
    private final DecisionRoundQuery decisionRounds;
    private final TurnItemsQuery turnItems;
    private final TurnReportPresenter presenter;

    public TurnReports(FleetSnapshotQuery fleet, MeetingCalendar calendar, PrimarySessions sessions, PrimaryHooks primaryHooks,
            DecisionRoundQuery decisionRounds, TurnItemsQuery turnItems, TurnReportPresenter presenter) {
        this.fleet = fleet;
        this.calendar = calendar;
        this.sessions = sessions;
        this.primaryHooks = primaryHooks;
        this.decisionRounds = decisionRounds;
        this.turnItems = turnItems;
        this.presenter = presenter;
    }

    public Optional<String> shownAtTheStopOf(UUID session, Integer paneWidth, Reply reply) {
        Optional<UUID> stopping = Optional.of(session);
        TurnReport report = reportOf(stopping, paneWidth);
        if (report.hasNothingForTheCaptain() || report.isNamedIn(reply)) {
            return Optional.empty();
        }
        recordTheReportShownIn(stopping);
        return Optional.of(presenter.text(report));
    }

    TurnReport shownIn(Optional<UUID> session, Integer paneWidth) {
        TurnReport report = reportOf(session, paneWidth);
        recordTheReportShownIn(session);
        return report;
    }

    private TurnReport reportOf(Optional<UUID> session, Integer paneWidth) {
        return new TurnReport(fleet.snapshot().waiting(), itemsRecordedIn(session), ReportWidth.forPaneIfKnown(paneWidth),
                calendar.unreadable().orElse(null), previousReportShownIn(session), decisionRounds.gathering().orElse(null));
    }

    Turn itemsRecordedIn(Optional<UUID> session) {
        return session.map(turnItems::recordedIn).orElse(Turn.NONE);
    }

    void recordTheReportShownIn(Optional<UUID> session) {
        session.ifPresent(primaryHooks::reportRendered);
    }

    private Instant previousReportShownIn(Optional<UUID> session) {
        return session.flatMap(sessions::previousReportOf).orElse(null);
    }
}
