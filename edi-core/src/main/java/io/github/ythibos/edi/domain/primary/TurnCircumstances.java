package io.github.ythibos.edi.domain.primary;

public record TurnCircumstances(boolean stateChanged, boolean chatKeptQuiet, boolean workInFlight, boolean waitIsLive) {

    boolean needsTheReport() {
        return stateChanged && !chatKeptQuiet;
    }

    boolean needsAWait() {
        return workInFlight && !waitIsLive;
    }
}
