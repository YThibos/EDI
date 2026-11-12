package io.github.ythibos.edi.domain.spark;

import java.time.Instant;
import java.util.Objects;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.rule.Rule;

public record SparkSorting(SparkOutcome outcome, WorkKey item, String reason, Instant sortedAt) {

    public SparkSorting {
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(sortedAt);
        if (outcome.namesABacklogItem() != (item != null)) {
            throw new IllegalArgumentException("A sorted spark names its backlog item exactly when it was backlogged or folded.");
        }
        if ((outcome == SparkOutcome.DROPPED) != (reason != null)) {
            throw new IllegalArgumentException("A sorted spark carries a reason exactly when it was dropped.");
        }
    }

    public static SparkSorting backlogged(WorkKey item, Instant at) {
        return new SparkSorting(SparkOutcome.BACKLOGGED, Objects.requireNonNull(item), null, at);
    }

    public static SparkSorting foldedInto(WorkKey item, Instant at) {
        return new SparkSorting(SparkOutcome.FOLDED, Objects.requireNonNull(item), null, at);
    }

    public static SparkSorting dropped(String reason, Instant at) {
        String why = Objects.requireNonNullElse(reason, "").strip();
        if (why.isEmpty()) {
            throw new Refusal(Rule.SPARK_SORTED_ONCE, "A dropped spark says why it was dropped: give the reason.");
        }
        return new SparkSorting(SparkOutcome.DROPPED, null, why, at);
    }
}
