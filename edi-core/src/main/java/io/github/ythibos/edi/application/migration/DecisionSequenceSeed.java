package io.github.ythibos.edi.application.migration;

import io.github.ythibos.edi.domain.decision.DecisionNumber;

public record DecisionSequenceSeed(int highest, String source) {

    public DecisionNumber firstNewNumber() {
        return new DecisionNumber(highest + 1);
    }
}
