package io.github.ythibos.edi.domain.dispatch;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.TaskSize;

public record DispatchRules(List<ProjectOverride> projectOverrides, List<DispatchCategory> categories, PipelineAgent pipelineAgent) {

    private static final ProjectName EDI = new ProjectName("EDI");

    public DispatchRules {
        projectOverrides = List.copyOf(projectOverrides);
        categories = List.copyOf(categories);
        Objects.requireNonNull(pipelineAgent, "The dispatch rules name the route of the validation pipeline's agent.");
        requireOneEntryWithoutSizesPerCategory(categories);
        requireNoSizeCoveredTwice(categories);
    }

    public DispatchRules validatedAgainst(ModelRoster roster) {
        projectOverrides.forEach(override -> requireAllowedAndExplained(override, roster));
        projectOverrides.forEach(this::requireCoveredCategoriesExist);
        requireEveryOverrideReachable();
        categories.forEach(category -> requireAllowedAndExplained(category, roster));
        requireAllowedAndExplained(pipelineAgent, roster);
        requireResearchFirstOnlyWhereWorkIsUnplanned();
        requireResearchersNoWeakerThanImplementers(roster);
        return this;
    }

    public DispatchCategory category(CategoryId id) {
        return categories.stream()
                .filter(category -> category.id().equals(id) && !category.namesSizes())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No dispatch category is called " + id + "; the categories are "
                        + categoryNames() + "."));
    }

    public ChosenRoute routeFor(List<ProjectName> projects, CategoryId category, TaskSize size, CaptainOverride captainsOverride) {
        ChosenRoute categoryRoute = categoryEntryFor(category, size).chosenFor(size);
        if (captainsOverride != null) {
            return captainsOverride.chosen();
        }
        return projectOverrideFor(projects, category, size).map(override -> override.chosenFor(category, size)).orElse(categoryRoute);
    }

    public ResearchFirst researchFirstFor(List<ProjectName> projects, CategoryId category, boolean causeUnknown) {
        DispatchCategory chosen = category(category);
        if (!chosen.id().worksWithoutAPlan(causeUnknown) || isEdiItself(projects)) {
            return ResearchFirst.NOT_APPLICABLE;
        }
        return chosen.researchFirst() ? ResearchFirst.QUEUES_RESEARCH : ResearchFirst.SWITCHED_OFF;
    }

    private static void requireOneEntryWithoutSizesPerCategory(List<DispatchCategory> categories) {
        categories.stream().map(DispatchCategory::id).distinct().forEach(id -> requireOneEntryWithoutSizes(id, categories));
    }

    private static void requireOneEntryWithoutSizes(CategoryId id, List<DispatchCategory> categories) {
        long withoutSizes = categories.stream().filter(category -> category.id().equals(id) && !category.namesSizes()).count();
        if (withoutSizes > 1) {
            throw new IllegalArgumentException("The dispatch rules name each category once; a further entry for " + id
                    + " names the sizes it covers.");
        }
        if (withoutSizes == 0) {
            throw new IllegalArgumentException("Every entry for the category " + id + " names sizes, so a task of another size or of none"
                    + " has no route; one entry for " + id + " names no sizes.");
        }
    }

    private static void requireNoSizeCoveredTwice(List<DispatchCategory> categories) {
        categories.stream()
                .filter(DispatchCategory::namesSizes)
                .filter(sized -> categories.stream().anyMatch(other -> other != sized && sized.sharesASizeWith(other)))
                .findFirst()
                .ifPresent(sized -> {
                    throw new IllegalArgumentException("Two entries for the category " + sized.id() + " cover the same size; each size of a"
                            + " category has one route.");
                });
    }

    private static void requireAllowedAndExplained(ProjectOverride override, ModelRoster roster) {
        String usedBy = override.name();
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

    private void requireEveryOverrideReachable() {
        IntStream.range(0, projectOverrides.size()).forEach(this::requireReachable);
    }

    private void requireReachable(int position) {
        ProjectOverride later = projectOverrides.get(position);
        projectOverrides.subList(0, position).stream()
                .filter(earlier -> earlier.leavesNothingFor(later))
                .findFirst()
                .ifPresent(earlier -> {
                    throw new IllegalArgumentException(later.name() + " never applies: the earlier override for " + earlier.scope()
                            + " covers every task it covers; put the narrower override first.");
                });
    }

    private static void requireAllowedAndExplained(DispatchCategory category, ModelRoster roster) {
        String usedBy = category.name();
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
        categories.stream()
                .filter(category -> category.id().equals(CategoryId.IMPLEMENTATION))
                .filter(implementation -> roster.isWeaker(researcher, implementation.route().model()))
                .findFirst()
                .ifPresent(implementation -> {
                    throw new IllegalArgumentException("Research before implementation needs a researcher at least as strong as the implementer, but"
                            + " research runs " + researcher + " and " + implementation.scope() + " runs "
                            + implementation.route().model() + ".");
                });
    }

    private DispatchCategory categoryEntryFor(CategoryId id, TaskSize size) {
        return categories.stream()
                .filter(category -> category.id().equals(id) && category.covers(size))
                .findFirst()
                .orElseGet(() -> category(id));
    }

    private boolean namesCategory(CategoryId id) {
        return categories.stream().anyMatch(category -> category.id().equals(id));
    }

    private String categoryNames() {
        return categories.stream().map(category -> category.id().value()).distinct().collect(Collectors.joining(", "));
    }

    private Optional<ProjectOverride> projectOverrideFor(List<ProjectName> projects, CategoryId category, TaskSize size) {
        return projectOverrides.stream().filter(override -> override.covers(projects, category, size)).findFirst();
    }

    private static boolean isEdiItself(List<ProjectName> projects) {
        return projects.contains(EDI);
    }
}
