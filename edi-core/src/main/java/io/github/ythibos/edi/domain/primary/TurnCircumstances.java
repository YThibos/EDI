package io.github.ythibos.edi.domain.primary;

import java.time.Instant;

public record TurnCircumstances(boolean stateChanged, Instant lastItemRecordedAt, boolean chatKeptQuiet, boolean workInFlight, boolean waitIsLive) {

    boolean needsTheReport(PrimaryTurn turn) {
        return !chatKeptQuiet && (unreportedStateChange(turn) || itemRecordedSinceTheReport(turn));
    }

    boolean needsAWait() {
        return workInFlight && !waitIsLive;
    }

    private boolean unreportedStateChange(PrimaryTurn turn) {
        return stateChanged && !turn.renderedTheReport();
    }

    private boolean itemRecordedSinceTheReport(PrimaryTurn turn) {
        return lastItemRecordedAt != null && !turn.reportedSince(lastItemRecordedAt);
    }
}
