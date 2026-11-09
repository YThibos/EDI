package io.github.ythibos.edi.domain.delivery;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.TokenUsage;

public record TaskCost(TaskId task, TokenUsage workers, TokenUsage pipeline, Instant countedAt) {

    public TaskCost {
        Objects.requireNonNull(task, "A cost is the cost of a task.");
        Objects.requireNonNull(workers, () -> "The cost of " + task + " counts its workers' tokens.");
        Objects.requireNonNull(countedAt, () -> "The cost of " + task + " records when it was counted.");
    }

    public TaskCost withPipelineSpend(TokenUsage spentByThePipeline) {
        return new TaskCost(task, workers, spentByThePipeline, countedAt);
    }

    public Optional<TokenUsage> pipelineIfKnown() {
        return Optional.ofNullable(pipeline);
    }

    public Optional<Long> totalIfKnown() {
        return pipelineIfKnown().map(spentByThePipeline -> workers.total() + spentByThePipeline.total());
    }
}
