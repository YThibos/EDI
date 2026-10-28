package io.github.ythibos.edi.web.report;

import java.time.Instant;

import io.github.ythibos.edi.application.fleet.WaitingOnTheCaptain;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.presence.UnreadableCalendar;

public record TurnReport(WaitingOnTheCaptain waiting, Turn turn, ReportWidth width, UnreadableCalendar unreadableCalendar, Instant previousReportAt) {

    public boolean showsTheCardOf(Decision decision) {
        return previousReportAt == null || decision.raisedAt().isAfter(previousReportAt);
    }

    public boolean showsTheUnreadableCalendar() {
        return unreadableCalendar != null;
    }

    public boolean hasNothingForTheCaptain() {
        return !showsTheUnreadableCalendar() && waiting.isEmpty() && turn.done().isEmpty() && turn.next().isEmpty();
    }
}
