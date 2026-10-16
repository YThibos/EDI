package io.github.ythibos.edi.domain.guard;

import java.util.Objects;

public record GuardRefusal(GuardRule rule, String reason) {

    public GuardRefusal {
        Objects.requireNonNull(rule, "A refusal names the rule it applies.");
        Objects.requireNonNull(reason, "A refusal says why.");
    }
}
