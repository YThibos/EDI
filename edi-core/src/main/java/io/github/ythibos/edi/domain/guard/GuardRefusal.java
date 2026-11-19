package io.github.ythibos.edi.domain.guard;

import java.util.Objects;

import io.github.ythibos.edi.domain.rule.Rule;

public record GuardRefusal(Rule rule, String act, String reason) {

    public GuardRefusal {
        Objects.requireNonNull(rule, "A refusal names the rule it applies.");
        Objects.requireNonNull(act, "A refusal names the act it judged.");
        Objects.requireNonNull(reason, "A refusal says why.");
    }

    public String verdict() {
        return "The guard refuses " + act + " under the rule " + rule.id() + ": " + reason;
    }

    public String judgement() {
        return act + ": " + reason;
    }
}
