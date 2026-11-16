package io.github.ythibos.edi.application.plan;

import java.util.List;

import io.github.ythibos.edi.domain.plan.Plan;
import io.github.ythibos.edi.domain.plan.SliceStanding;

public record PlanStandings(Plan plan, List<SliceStanding> slices) {

    public PlanStandings {
        slices = List.copyOf(slices);
    }
}
