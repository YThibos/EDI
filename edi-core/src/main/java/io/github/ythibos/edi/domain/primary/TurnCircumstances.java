package io.github.ythibos.edi.domain.primary;

import java.time.Instant;

public record TurnCircumstances(boolean somethingReachedTheCaptain, Instant lastItemRecordedAt, boolean chatKeptQuiet, boolean workInFlight,
        boolean waitIsLive, boolean answerUndelivered) {

    boolean needsTheReport(PrimaryTurn turn) {
        return !chatKeptQuiet && (unreportedChangeForTheCaptain(turn) || itemRecordedSinceTheReport(turn));
    }

    boolean needsAWait() {
        return workInFlight && !waitIsLive;
    }

    private boolean unreportedChangeForTheCaptain(PrimaryTurn turn) {
        return somethingReachedTheCaptain && !turn.renderedTheReport();
    }

    private boolean itemRecordedSinceTheReport(PrimaryTurn turn) {
        return lastItemRecordedAt != null && !turn.reportedSince(lastItemRecordedAt);
    }
}
