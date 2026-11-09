package io.github.ythibos.edi.application.delivery;

import java.time.LocalDate;
import java.util.List;

import io.github.ythibos.edi.domain.delivery.TaskCost;

public record RecountedCosts(LocalDate since, int counted, List<Recount> changed) {

    public RecountedCosts {
        changed = List.copyOf(changed);
    }

    public record Recount(TaskCost before, TaskCost after) {
    }
}
