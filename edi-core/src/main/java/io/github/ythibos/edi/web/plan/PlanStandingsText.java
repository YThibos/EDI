package io.github.ythibos.edi.web.plan;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.plan.PlanStandings;
import io.github.ythibos.edi.domain.plan.Plan;
import io.github.ythibos.edi.domain.plan.PlanHeading;
import io.github.ythibos.edi.domain.plan.SliceStanding;
import io.github.ythibos.edi.domain.task.Wait;
import io.github.ythibos.edi.web.text.Table;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.Words;

@Component
class PlanStandingsText implements TextPresenter<PlanStandings> {

    private static final String INDENT = "  ";

    @Override
    public Class<PlanStandings> presents() {
        return PlanStandings.class;
    }

    @Override
    public String text(PlanStandings standings) {
        Table table = new Table(INDENT);
        headingOf(standings.plan().heading(), table);
        table.line("Slices");
        standings.slices().forEach(slice -> table.row(cellsOf(slice, standings.plan())));
        return table.text();
    }

    private static void headingOf(PlanHeading heading, Table table) {
        table.line(heading.key() + " plan: " + heading.title())
                .line("Project: " + heading.project())
                .line("Report: " + heading.report())
                .line("Go: " + heading.go())
                .line("Capacity: " + Words.quantity(heading.capacity(), "slice") + " at once");
        if (heading.effort() != null) {
            table.line("Effort: " + heading.effort().word());
        }
    }

    private static String[] cellsOf(SliceStanding standing, Plan plan) {
        return new String[] {
            String.valueOf(standing.slice().number()),
            standing.task().id().value(),
            standing.task().intake().workKey().toString(),
            standing.task().stage().word(),
            standing.slice().awaits().words(),
            readinessOf(standing, plan)};
    }

    private static String readinessOf(SliceStanding standing, Plan plan) {
        return standing.isEligible() ? "eligible" : whatHoldsItBack(standing, plan);
    }

    private static String whatHoldsItBack(SliceStanding standing, Plan plan) {
        List<String> reasons = new ArrayList<>();
        if (standing.task().isHeld()) {
            reasons.add(Words.hold(standing.task().hold()));
        }
        List<Wait> outsideThePlan = standing.unmetWaitsOutside(plan);
        if (!outsideThePlan.isEmpty()) {
            reasons.add(Words.waits(outsideThePlan));
        }
        return String.join("; ", reasons);
    }
}
