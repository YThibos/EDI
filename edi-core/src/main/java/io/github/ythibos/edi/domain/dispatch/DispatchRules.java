package io.github.ythibos.edi.domain.dispatch;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.ythibos.edi.domain.project.ProjectName;

public record DispatchRules(List<ProjectOverride> projectOverrides, List<DispatchCategory> categories, PipelineAgent pipelineAgent) {

    private static final ProjectName EDI = new ProjectName("EDI");

    public DispatchRules {
        projectOverrides = List.copyOf(projectOverrides);
        categories = List.copyOf(categories);
        Objects.requireNonNull(pipelineAgent, "The dispatch rules name the route of the validation pipeline's agent.");
        if (new HashSet<>(categories.stream().map(DispatchCategory::id).toList()).size() < categories.size()) {
            throw new IllegalArgumentException("The dispatch rules name each category once.");
        }
    }

    public DispatchRules validatedAgainst(ModelRoster roster) {
        projectOverrides.forEach(override -> requireAllowedAndExplained(override, roster));
        projectOverrides.forEach(this::requireCoveredCategoriesExist);
        categories.forEach(category -> requireAllowedAndExplained(category, roster));
        requireAllowedAndExplained(pipelineAgent, roster);
        requireResearchFirstOnlyWhereWorkIsUnplanned();
        requireResearchersNoWeakerThanImplementers(roster);
        return this;
    }

    public DispatchCategory category(CategoryId id) {
        return categories.stream()
                .filter(category -> category.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No dispatch category is called " + id + "; the categories are "
                        + categoryNames() + "."));
    }

    public Route routeFor(List<ProjectName> projects, CategoryId category, CaptainOverride captainsOverride) {
        Route categoryRoute = category(category).route();
        if (captainsOverride != null) {
            return captainsOverride.route();
        }
        return projectOverrideFor(projects, category).map(ProjectOverride::route).orElse(categoryRoute);
    }

    public ResearchFirst researchFirstFor(List<ProjectName> projects, CategoryId category, boolean causeUnknown) {
        DispatchCategory chosen = category(category);
        if (!chosen.id().worksWithoutAPlan(causeUnknown) || isEdiItself(projects)) {
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

    private void requireCoveredCategoriesExist(ProjectOverride override) {
        override.categories().stream()
                .filter(covered -> !namesCategory(covered))
                .findFirst()
                .ifPresent(unknown -> {
                    throw new IllegalArgumentException("The override for " + override.project() + " covers " + unknown
                            + ", which is no dispatch category; the categories are " + categoryNames() + ".");
                });
    }

    private static void requireAllowedAndExplained(DispatchCategory category, ModelRoster roster) {
        String usedBy = "The category " + category.id();
        if (!category.statesWhy()) {
            throw new IllegalArgumentException(usedBy + " states no why.");
        }
        roster.requireAllows(category.route(), usedBy);
    }

    private static void requireAllowedAndExplained(PipelineAgent agent, ModelRoster roster) {
        String usedBy = "The pipeline entry";
        if (!agent.statesWhy()) {
            throw new IllegalArgumentException(usedBy + " states no why.");
        }
        roster.requireAllows(agent.route(), usedBy);
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

    private boolean namesCategory(CategoryId id) {
        return categories.stream().anyMatch(category -> category.id().equals(id));
    }

    private String categoryNames() {
        return categories.stream().map(category -> category.id().value()).collect(Collectors.joining(", "));
    }

    private Optional<ProjectOverride> projectOverrideFor(List<ProjectName> projects, CategoryId category) {
        return projectOverrides.stream().filter(override -> override.covers(projects, category)).findFirst();
    }

    private static boolean isEdiItself(List<ProjectName> projects) {
        return projects.contains(EDI);
    }
}
