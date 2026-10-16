package io.github.ythibos.edi.application.decision;

import java.util.List;

import io.github.ythibos.edi.domain.decision.Decision;

public record DecisionList(boolean openOnly, List<Decision> decisions) {

    public DecisionList {
        decisions = List.copyOf(decisions);
    }
}
