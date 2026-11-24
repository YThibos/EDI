package io.github.ythibos.edi.domain.plan;

import java.util.List;

public record PlanStandings(Plan plan, List<SliceStanding> slices) {

    public PlanStandings {
        slices = List.copyOf(slices);
    }

    public List<SliceStanding> eligible() {
        return slices.stream().filter(SliceStanding::isEligible).toList();
    }

    public long freePlaces() {
        return Math.max(0, plan.heading().capacity() - slices.stream().filter(SliceStanding::isWorking).count());
    }
}
