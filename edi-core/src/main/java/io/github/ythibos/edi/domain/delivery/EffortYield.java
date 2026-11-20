package io.github.ythibos.edi.domain.delivery;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Function;

import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.worker.TokenUsage;

public record EffortYield(EffortLevel effort, int tasks, TokenUsage workers, TokenUsage pipeline, int reviewRounds, int findings) {

    private static final Comparator<EffortLevel> NO_EFFORT_FLAG_FIRST = Comparator.nullsFirst(Comparator.naturalOrder());

    public EffortYield {
        if (tasks < 1) {
            throw new IllegalArgumentException("A yield is the yield of at least one task.");
        }
        Objects.requireNonNull(workers, "A yield counts the workers' tokens.");
        Objects.requireNonNull(pipeline, "A yield counts the pipeline's tokens.");
    }

    public static List<EffortYield> perEffort(Collection<ReviewedCost> reviewedCosts) {
        List<ReviewedCost> routed = reviewedCosts.stream().filter(ReviewedCost::knowsItsRoute).toList();
        return routed.stream()
                .map(ReviewedCost::effort)
                .distinct()
                .sorted(NO_EFFORT_FLAG_FIRST)
                .map(effort -> yieldAt(effort, routed.stream().filter(reviewed -> Objects.equals(reviewed.effort(), effort)).toList()))
                .toList();
    }

    public OptionalDouble findingsPerReviewRound() {
        return reviewRounds == 0 ? OptionalDouble.empty() : OptionalDouble.of((double) findings / reviewRounds);
    }

    public long weightedWorkerTokensPerTask() {
        return Math.round((double) workers.weighted() / tasks);
    }

    private static EffortYield yieldAt(EffortLevel effort, List<ReviewedCost> reviewedCosts) {
        return new EffortYield(effort, reviewedCosts.size(),
                spentBy(reviewedCosts, reviewed -> reviewed.cost().workers()),
                spentBy(reviewedCosts, reviewed -> reviewed.cost().pipelineIfKnown().orElse(TokenUsage.NONE)),
                counted(reviewedCosts, PipelineReview::roundsIfKnown),
                counted(reviewedCosts, PipelineReview::findingsIfKnown));
    }

    private static TokenUsage spentBy(List<ReviewedCost> reviewedCosts, Function<ReviewedCost, TokenUsage> spender) {
        return reviewedCosts.stream().map(spender).reduce(TokenUsage.NONE, TokenUsage::plus);
    }

    private static int counted(List<ReviewedCost> reviewedCosts, Function<PipelineReview, OptionalInt> count) {
        return reviewedCosts.stream().map(ReviewedCost::review).map(count).mapToInt(known -> known.orElse(0)).sum();
    }
}
