package io.github.ythibos.edi.domain.dispatch;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.ythibos.edi.domain.project.ProjectName;

public record DispatchRules(List<ProjectOverride> projectOverrides, List<DispatchCategory> categories) {

    public DispatchRules {
        projectOverrides = List.copyOf(projectOverrides);
        categories = List.copyOf(categories);
        if (new HashSet<>(categories.stream().map(DispatchCategory::id).toList()).size() < categories.size()) {
            throw new IllegalArgumentException("The dispatch rules name each category once.");
        }
    }

    public DispatchRules validatedAgainst(ModelRoster roster) {
        projectOverrides.forEach(override -> requireAllowedAndExplained(override, roster));
        categories.forEach(category -> requireAllowedAndExplained(category, roster));
        requireResearchFirstOnlyWhereWorkIsUnplanned();
        requireResearchersNoWeakerThanImplementers(roster);
        return this;
    }

    public DispatchCategory category(CategoryId id) {
        return categories.stream()
                .filter(category -> category.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No dispatch category is called " + id + "; the categories are "
                        + categories.stream().map(category -> category.id().value()).collect(Collectors.joining(", ")) + "."));
    }

    public Route routeFor(List<ProjectName> projects, CategoryId category, CaptainOverride captainsOverride) {
        Route categoryRoute = category(category).route();
        if (captainsOverride != null) {
            return captainsOverride.route();
        }
        return projectOverrideFor(projects).map(ProjectOverride::route).orElse(categoryRoute);
    }

    public ResearchFirst researchFirstFor(List<ProjectName> projects, CategoryId category, boolean causeUnknown) {
        DispatchCategory chosen = category(category);
        if (!chosen.id().worksWithoutAPlan(causeUnknown) || projectOverrideFor(projects).isPresent()) {
            return ResearchFirst.NOT_APPLICABLE;
        }
        return chosen.researchFirst() ? ResearchFirst.QUEUES_RESEARCH : ResearchFirst.SWITCHED_OFF;
    }

    private static void requireAllowedAndExplained(ProjectOverride override, ModelRoster roster) {
        String usedBy = "The override for " + override.project();
        if (!override.statesWhy()) {
            throw new IllegalArgumentException(usedBy + " states no why.");
        }
        roster.requireAllows(override.route(), usedBy);
    }

    private static void requireAllowedAndExplained(DispatchCategory category, ModelRoster roster) {
        String usedBy = "The category " + category.id();
        if (!category.statesWhy()) {
            throw new IllegalArgumentException(usedBy + " states no why.");
        }
        roster.requireAllows(category.route(), usedBy);
    }

    private void requireResearchFirstOnlyWhereWorkIsUnplanned() {
        categories.stream()
                .filter(category -> category.researchFirst() && !category.id().takesAResearchFirstSwitch())
                .findFirst()
                .ifPresent(category -> {
                    throw new IllegalArgumentException("The category " + category.id() + " cannot switch research first on; only "
                            + CategoryId.IMPLEMENTATION_UNPLANNED + " and " + CategoryId.BUGFIX + " work without a settled plan.");
                });
    }

    private void requireResearchersNoWeakerThanImplementers(ModelRoster roster) {
        if (categories.stream().noneMatch(DispatchCategory::researchFirst)) {
            return;
        }
        ModelId researcher = category(CategoryId.RESEARCH).route().model();
        ModelId implementer = category(CategoryId.IMPLEMENTATION).route().model();
        if (roster.isWeaker(researcher, implementer)) {
            throw new IllegalArgumentException("Research before implementation needs a researcher at least as strong as the implementer, but research runs "
                    + researcher + " and implementation runs " + implementer + ".");
        }
    }

    private Optional<ProjectOverride> projectOverrideFor(List<ProjectName> projects) {
        return projectOverrides.stream().filter(override -> override.covers(projects)).findFirst();
    }
}
