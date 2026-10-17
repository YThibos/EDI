package io.github.ythibos.edi.domain.dispatch;

import java.util.Objects;
import java.util.OptionalDouble;

import io.github.ythibos.edi.domain.task.TaskId;

public record ShipMeasure(TaskId ship, Long tokens, Integer reviewRounds, Integer findings, int reviewFixes) {

    public ShipMeasure {
        Objects.requireNonNull(ship, "A measure is of one ship.");
        if (reviewFixes < 0) {
            throw new IllegalArgumentException(ship + " counts its review fixes from zero.");
        }
    }

    OptionalDouble tokensIfKnown() {
        return tokens == null ? OptionalDouble.empty() : OptionalDouble.of(tokens);
    }

    OptionalDouble reviewRoundsIfKnown() {
        return reviewRounds == null ? OptionalDouble.empty() : OptionalDouble.of(reviewRounds);
    }

    OptionalDouble findingsIfKnown() {
        return findings == null ? OptionalDouble.empty() : OptionalDouble.of(findings);
    }

    OptionalDouble reviewFixesCounted() {
        return OptionalDouble.of(reviewFixes);
    }
}
