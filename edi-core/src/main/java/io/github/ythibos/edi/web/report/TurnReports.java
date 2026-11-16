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

    public String handedOverIn(UUID session, Integer paneWidth) {
        return presenter.text(shownIn(Optional.of(session), paneWidth));
    }

    TurnReport shownIn(Optional<UUID> session, Integer paneWidth) {
        TurnReport report = new TurnReport(fleet.snapshot().waiting(), itemsRecordedIn(session), ReportWidth.forPaneIfKnown(paneWidth),
                calendar.unreadable().orElse(null), previousReportShownIn(session), decisionRounds.gathering().orElse(null));
        recordTheReportShownIn(session);
        return report;
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
