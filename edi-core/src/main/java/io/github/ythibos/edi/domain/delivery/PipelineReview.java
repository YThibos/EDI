package io.github.ythibos.edi.domain.delivery;

import java.util.OptionalInt;

public record PipelineReview(Integer rounds, Integer findings) {

    private static final PipelineReview UNKNOWN = new PipelineReview(null, null);

    public PipelineReview {
        if (rounds != null && rounds < 0 || findings != null && findings < 0) {
            throw new IllegalArgumentException("A review counts its rounds and findings from zero.");
        }
    }

    public static PipelineReview unknown() {
        return UNKNOWN;
    }

    public OptionalInt roundsIfKnown() {
        return rounds == null ? OptionalInt.empty() : OptionalInt.of(rounds);
    }

    public OptionalInt findingsIfKnown() {
        return findings == null ? OptionalInt.empty() : OptionalInt.of(findings);
    }
}
