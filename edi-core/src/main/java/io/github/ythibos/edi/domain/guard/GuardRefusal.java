package io.github.ythibos.edi.domain.guard;

import java.util.Objects;

import io.github.ythibos.edi.domain.rule.Rule;

public record GuardRefusal(Rule rule, String reason) {

    public GuardRefusal {
        Objects.requireNonNull(rule, "A refusal names the rule it applies.");
        Objects.requireNonNull(reason, "A refusal says why.");
    }
}
