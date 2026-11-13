package io.github.ythibos.edi.domain.primary;

public record TurnCircumstances(boolean stateChanged, boolean itemsRecorded, boolean chatKeptQuiet, boolean workInFlight, boolean waitIsLive) {

    boolean needsTheReport() {
        return (stateChanged || itemsRecorded) && !chatKeptQuiet;
    }

    boolean needsAWait() {
        return workInFlight && !waitIsLive;
    }
}
