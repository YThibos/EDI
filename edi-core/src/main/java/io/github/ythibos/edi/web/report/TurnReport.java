package io.github.ythibos.edi.web.report;

import java.time.Instant;

import io.github.ythibos.edi.application.fleet.OpenDecision;
import io.github.ythibos.edi.application.fleet.WaitingOnTheCaptain;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionRound;
import io.github.ythibos.edi.domain.presence.UnreadableCalendar;

public record TurnReport(WaitingOnTheCaptain waiting, Turn turn, ReportWidth width, UnreadableCalendar unreadableCalendar, Instant previousReportAt,
        DecisionRound gatheringRound) {

    public boolean holdsForTheGatheringRound(Decision decision) {
        return gatheringRound != null && gatheringRound.gathers(decision.number());
    }

    public boolean showsTheCardOf(OpenDecision open) {
        Decision decision = open.decision();
        return !DecisionRound.waitsForItsRound(decision, open.task()) && raisedSinceThePreviousReport(decision);
    }

    public boolean showsTheUnreadableCalendar() {
        return unreadableCalendar != null;
    }

    public boolean hasNothingForTheCaptain() {
        return !showsTheUnreadableCalendar() && waiting.hasNothingWaiting() && turn.done().isEmpty() && turn.next().isEmpty();
    }

    private boolean raisedSinceThePreviousReport(Decision decision) {
        return previousReportAt == null || decision.raisedAt().isAfter(previousReportAt);
    }
}
