package io.github.ythibos.edi.domain.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;
import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.TokenUsage;

class EffortYieldTest {

    private static final Instant COUNTED = Instant.parse("2026-11-16T10:00:00Z");
    private static final Route OPUS_HIGH = new Route(new ModelId("claude-opus-5-5"), EffortLevel.HIGH);
    private static final Route SONNET_HIGH = new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.HIGH);
    private static final Route OPUS_XHIGH = new Route(new ModelId("claude-opus-5-5"), EffortLevel.XHIGH);

    @Test
    void showsEachEffortsWeightedTokensAndFindingsPerReviewRound() {
        List<ReviewedCost> week = List.of(
                reviewed("fit-lamp", OPUS_HIGH, new TokenUsage(1_000_000, 10_000_000, 100_000, 50_000), new TokenUsage(200_000, 2_000_000, 0, 20_000),
                        new PipelineReview(4, 6)),
                reviewed("paint-buoy", SONNET_HIGH, new TokenUsage(500_000, 0, 0, 25_000), null, new PipelineReview(2, 1)),
                reviewed("chart-reef", OPUS_XHIGH, new TokenUsage(2_000_000, 20_000_000, 400_000, 200_000), TokenUsage.NONE, new PipelineReview(1, 0)),
                reviewed("map-caves", OPUS_XHIGH, new TokenUsage(100_000, 0, 0, 0), null, PipelineReview.unknown()));

        List<EffortYield> perEffort = EffortYield.perEffort(week);

        assertThat(perEffort).extracting(EffortYield::effort, EffortYield::tasks, yield -> yield.workers().weighted(),
                EffortYield::weightedWorkerTokensPerTask, yield -> yield.pipeline().weighted(), EffortYield::reviewRounds, EffortYield::findings)
                .containsExactly(
                        tuple(EffortLevel.HIGH, 2, 3_000_000L, 1_500_000L, 500_000L, 6, 7),
                        tuple(EffortLevel.XHIGH, 2, 5_600_000L, 2_800_000L, 0L, 1, 0));
        assertThat(perEffort.getFirst().findingsPerReviewRound().getAsDouble()).isEqualTo(7.0 / 6);
        assertThat(perEffort.getLast().findingsPerReviewRound()).hasValue(0);
    }

    @Test
    void keepsTheRawCountsBesideTheWeightedOnes() {
        List<EffortYield> perEffort = EffortYield.perEffort(List.of(
                reviewed("fit-lamp", OPUS_HIGH, new TokenUsage(10, 200, 30, 4), new TokenUsage(1, 2, 3, 4), new PipelineReview(1, 1)),
                reviewed("paint-buoy", OPUS_HIGH, new TokenUsage(1, 2, 3, 4), new TokenUsage(10, 20, 30, 40), new PipelineReview(1, 0))));

        assertThat(perEffort.getFirst().workers()).isEqualTo(new TokenUsage(11, 202, 33, 8));
        assertThat(perEffort.getFirst().pipeline()).isEqualTo(new TokenUsage(11, 22, 33, 44));
    }

    @Test
    void listsAWorkerWithoutAnEffortFlagFirstAndLeavesOutACostWithoutARoute() {
        List<EffortYield> perEffort = EffortYield.perEffort(List.of(
                reviewed("fit-lamp", OPUS_XHIGH, new TokenUsage(10, 0, 0, 0), null, PipelineReview.unknown()),
                reviewed("paint-buoy", new Route(new ModelId("claude-sonnet-5-5"), null), new TokenUsage(20, 0, 0, 0), null, PipelineReview.unknown()),
                reviewed("chart-reef", null, new TokenUsage(40, 0, 0, 0), null, new PipelineReview(3, 3))));

        assertThat(perEffort).extracting(EffortYield::effort, EffortYield::tasks, yield -> yield.workers().weighted())
                .containsExactly(tuple(null, 1, 20L), tuple(EffortLevel.XHIGH, 1, 10L));
    }

    @Test
    void knowsNoFindingsPerRoundWithoutAReviewRound() {
        EffortYield unreviewed = EffortYield.perEffort(List.of(reviewed("map-caves", OPUS_HIGH, TokenUsage.NONE, null, PipelineReview.unknown()))).getFirst();

        assertThat(unreviewed.findingsPerReviewRound()).isEqualTo(OptionalDouble.empty());
        assertThat(unreviewed.reviewRounds()).isZero();
    }

    @Test
    void isTheYieldOfAtLeastOneTask() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EffortYield(EffortLevel.HIGH, 0, TokenUsage.NONE, TokenUsage.NONE, 0, 0))
                .withMessage("A yield is the yield of at least one task.");
    }

    private static ReviewedCost reviewed(String task, Route route, TokenUsage workers, TokenUsage pipeline, PipelineReview review) {
        return new ReviewedCost(new TaskCost(new TaskId(task), route, workers, pipeline, COUNTED), review);
    }
}
