package io.github.ythibos.edi.domain.delivery;

import java.util.Objects;

import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.Route;

public record ReviewedCost(TaskCost cost, PipelineReview review) {

    public ReviewedCost {
        Objects.requireNonNull(cost, "A reviewed cost is a task's cost.");
        Objects.requireNonNull(review, () -> "The cost of " + cost.task() + " comes with its review, known or not.");
    }

    public boolean knowsItsRoute() {
        return cost.routeIfKnown().isPresent();
    }

    public EffortLevel effort() {
        return cost.routeIfKnown().map(Route::effort).orElse(null);
    }
}
