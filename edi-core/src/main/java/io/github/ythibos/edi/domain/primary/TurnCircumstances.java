package io.github.ythibos.edi.domain.primary;

import java.util.List;

import io.github.ythibos.edi.domain.decision.UndeliveredAnswer;

public record TurnCircumstances(boolean stateChanged, boolean chatKeptQuiet, boolean workInFlight, boolean waitIsLive,
        List<UndeliveredAnswer> undeliveredAnswers) {

    public TurnCircumstances {
        undeliveredAnswers = List.copyOf(undeliveredAnswers);
    }

    boolean needsTheReport() {
        return stateChanged && !chatKeptQuiet;
    }

    boolean needsAWait() {
        return workInFlight && !waitIsLive;
    }

    boolean leavesAnAnswerUndelivered() {
        return !undeliveredAnswers.isEmpty();
    }
}
