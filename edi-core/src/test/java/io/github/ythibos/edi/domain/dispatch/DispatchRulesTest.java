package io.github.ythibos.edi.domain.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.branch.BranchKind;
import io.github.ythibos.edi.domain.dispatch.ModelRoster.AllowedModel;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.TaskSize;

class DispatchRulesTest {

    private static final Route OPUS_HIGH = new Route(new ModelId("claude-opus-5-5"), EffortLevel.HIGH);
    private static final Route SONNET_HIGH = new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.HIGH);
    private static final Route SONNET_MEDIUM = new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.MEDIUM);
    private static final Route HAIKU = new Route(new ModelId("claude-haiku-5-5"), null);
    private static final ModelRoster ROSTER = new ModelRoster(List.of(
            new AllowedModel(new ModelId("claude-opus-5-5"), true),
            new AllowedModel(new ModelId("claude-sonnet-5-5"), true),
            new AllowedModel(new ModelId("claude-haiku-5-5"), false)));
    private static final List<DispatchCategory> CATEGORIES = List.of(
            new DispatchCategory(CategoryId.IMPLEMENTATION, Set.of(), "", SONNET_MEDIUM, "Planned work.", null, false),
            new DispatchCategory(CategoryId.MECHANICAL, Set.of(), "", HAIKU, "Rote work.", BranchKind.CHORE, false));
    private static final PipelineAgent PIPELINE_AGENT = new PipelineAgent(OPUS_HIGH, "The last judge before the captain.");
    private static final DispatchRules RULES = new DispatchRules(
            List.of(new ProjectOverride(new ProjectName("EDI"), Set.of(), Set.of(), OPUS_HIGH, "EDI runs on Opus.")), CATEGORIES, PIPELINE_AGENT);
    private static final List<ProjectName> EDI = List.of(new ProjectName("EDI"));

    @Test
    void routesTheCaptainsOverrideAboveTheProjectOverrideAboveTheCategory() {
        CaptainOverride captain = new CaptainOverride(new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.LOW), "the captain asked");

        assertThat(RULES.routeFor(List.of(new ProjectName("harness")), CategoryId.IMPLEMENTATION, null, null))
                .isEqualTo(ChosenRoute.chosenBy(SONNET_MEDIUM, "the category implementation"));
        assertThat(RULES.routeFor(EDI, CategoryId.IMPLEMENTATION, null, null)).isEqualTo(ChosenRoute.chosenBy(OPUS_HIGH, "the override for EDI on implementation"));
        assertThat(RULES.routeFor(EDI, CategoryId.IMPLEMENTATION, null, captain))
                .isEqualTo(ChosenRoute.chosenBy(captain.route(), "the captain's override: the captain asked"));
        assertThatIllegalArgumentException().isThrownBy(() -> RULES.routeFor(EDI, new CategoryId("flying"), null, null))
                .withMessage("No dispatch category is called flying; the categories are implementation, mechanical.");
    }

    @Test
    void routesAProjectOverrideOnlyForTheCategoriesItCovers() {
        DispatchRules scoped = new DispatchRules(
                List.of(new ProjectOverride(new ProjectName("EDI"), Set.of(CategoryId.IMPLEMENTATION), Set.of(), OPUS_HIGH, "EDI's implementation runs on Opus.")),
                CATEGORIES, PIPELINE_AGENT);

        assertThat(scoped.validatedAgainst(ROSTER)).isSameAs(scoped);
        assertThat(scoped.routeFor(EDI, CategoryId.IMPLEMENTATION, null, null).route()).isEqualTo(OPUS_HIGH);
        assertThat(scoped.routeFor(EDI, CategoryId.MECHANICAL, null, null).route()).isEqualTo(HAIKU);
        assertThat(RULES.routeFor(EDI, CategoryId.MECHANICAL, null, null).route()).isEqualTo(OPUS_HIGH);
    }

    @Test
    void routesASmallTaskByTheOverrideThatNamesItsSizeAndALargeOneByTheOverrideAfterIt() {
        DispatchRules bySize = new DispatchRules(List.of(
                new ProjectOverride(new ProjectName("EDI"), Set.of(CategoryId.IMPLEMENTATION), Set.of(TaskSize.XS, TaskSize.S), SONNET_HIGH,
                        "Small EDI items run on Sonnet."),
                new ProjectOverride(new ProjectName("EDI"), Set.of(CategoryId.IMPLEMENTATION), Set.of(), OPUS_HIGH, "EDI runs on Opus.")),
                CATEGORIES, PIPELINE_AGENT).validatedAgainst(ROSTER);

        assertThat(bySize.routeFor(EDI, CategoryId.IMPLEMENTATION, TaskSize.S, null))
                .isEqualTo(ChosenRoute.chosenBy(SONNET_HIGH, "the override for EDI on implementation of size S"));
        assertThat(bySize.routeFor(EDI, CategoryId.IMPLEMENTATION, TaskSize.L, null))
                .isEqualTo(ChosenRoute.chosenBy(OPUS_HIGH, "the override for EDI on implementation"));
        assertThat(bySize.routeFor(EDI, CategoryId.IMPLEMENTATION, null, null))
                .isEqualTo(ChosenRoute.chosenBy(OPUS_HIGH, "the override for EDI on implementation"));
        assertThat(bySize.routeFor(List.of(new ProjectName("harness")), CategoryId.IMPLEMENTATION, TaskSize.XS, null).route()).isEqualTo(SONNET_MEDIUM);
    }

    @Test
    void routesTheCaptainsOverrideAboveARuleThatNamesTheTasksSize() {
        DispatchRules bySize = new DispatchRules(List.of(new ProjectOverride(new ProjectName("EDI"), Set.of(), Set.of(TaskSize.S), SONNET_HIGH,
                "Small EDI items run on Sonnet.")), CATEGORIES, PIPELINE_AGENT);
        CaptainOverride captain = new CaptainOverride(OPUS_HIGH, "this one is subtle");

        assertThat(bySize.routeFor(EDI, CategoryId.IMPLEMENTATION, TaskSize.S, captain))
                .isEqualTo(ChosenRoute.chosenBy(OPUS_HIGH, "the captain's override: this one is subtle"));
    }

    @Test
    void routesACategoryEntryThatNamesSizesOnlyForATaskOfOneOfThem() {
        DispatchRules bySize = new DispatchRules(List.of(), List.of(
                new DispatchCategory(CategoryId.IMPLEMENTATION, Set.of(TaskSize.XS), null, HAIKU, "A tiny change is rote.", null, false),
                CATEGORIES.getFirst(), CATEGORIES.getLast()), PIPELINE_AGENT).validatedAgainst(ROSTER);

        assertThat(bySize.routeFor(EDI, CategoryId.IMPLEMENTATION, TaskSize.XS, null)).isEqualTo(ChosenRoute.chosenBy(HAIKU, "the category implementation of size XS"));
        assertThat(bySize.routeFor(EDI, CategoryId.IMPLEMENTATION, TaskSize.M, null)).isEqualTo(ChosenRoute.chosenBy(SONNET_MEDIUM, "the category implementation"));
        assertThat(bySize.routeFor(EDI, CategoryId.IMPLEMENTATION, null, null).route()).isEqualTo(SONNET_MEDIUM);
        assertThat(bySize.category(CategoryId.IMPLEMENTATION)).isEqualTo(CATEGORIES.getFirst());
    }

    @Test
    void refusesCategoryEntriesThatLeaveASizeWithoutARouteOrRouteOneTwice() {
        DispatchCategory tiny = new DispatchCategory(CategoryId.IMPLEMENTATION, Set.of(TaskSize.XS, TaskSize.S), null, HAIKU, "Rote.", null, false);
        DispatchCategory small = new DispatchCategory(CategoryId.IMPLEMENTATION, Set.of(TaskSize.S), null, SONNET_HIGH, "Small.", null, false);

        assertThatIllegalArgumentException().isThrownBy(() -> new DispatchRules(List.of(), List.of(tiny, CATEGORIES.getLast()), PIPELINE_AGENT))
                .withMessage("Every entry for the category implementation names sizes, so a task of another size or of none has no route; one entry for"
                        + " implementation names no sizes.");
        assertThatIllegalArgumentException().isThrownBy(() -> new DispatchRules(List.of(), List.of(tiny, small, CATEGORIES.getFirst()), PIPELINE_AGENT))
                .withMessage("Two entries for the category implementation cover the same size; each size of a category has one route.");
        assertThatIllegalArgumentException().isThrownBy(() -> new DispatchRules(List.of(), List.of(CATEGORIES.getFirst(), CATEGORIES.getFirst()), PIPELINE_AGENT))
                .withMessageStartingWith("The dispatch rules name each category once");
    }

    @Test
    void refusesAnOverrideThatAnEarlierOverrideOfItsProjectLeavesNothingToRoute() {
        DispatchRules shadowed = new DispatchRules(List.of(
                new ProjectOverride(new ProjectName("EDI"), Set.of(CategoryId.IMPLEMENTATION), Set.of(), OPUS_HIGH, "EDI runs on Opus."),
                new ProjectOverride(new ProjectName("EDI"), Set.of(CategoryId.IMPLEMENTATION), Set.of(TaskSize.XS, TaskSize.S), SONNET_HIGH,
                        "Small EDI items run on Sonnet.")),
                CATEGORIES, PIPELINE_AGENT);

        assertThatIllegalArgumentException().isThrownBy(() -> shadowed.validatedAgainst(ROSTER))
                .withMessage("The override for EDI on [implementation] of size XS, S never applies: the earlier override for EDI on [implementation]"
                        + " covers every task it covers; put the narrower override first.");
    }

    @Test
    void refusesAProjectOverrideThatCoversNoSuchCategory() {
        DispatchRules misspelt = new DispatchRules(
                List.of(new ProjectOverride(new ProjectName("EDI"), Set.of(new CategoryId("implementaton")), Set.of(), OPUS_HIGH, "EDI runs on Opus.")),
                CATEGORIES, PIPELINE_AGENT);

        assertThatIllegalArgumentException().isThrownBy(() -> misspelt.validatedAgainst(ROSTER))
                .withMessage("The override for EDI covers implementaton, which is no dispatch category; the categories are implementation, mechanical.");
    }

    @Test
    void acceptsAModelThatIgnoresTheEffortFlagOnlyWithoutOne() {
        assertThat(RULES.validatedAgainst(ROSTER)).isSameAs(RULES);
        DispatchRules effortForHaiku = new DispatchRules(List.of(),
                List.of(new DispatchCategory(CategoryId.MECHANICAL, Set.of(), "", new Route(new ModelId("claude-haiku-5-5"), EffortLevel.LOW), "Rote.", null, false)),
                PIPELINE_AGENT);

        assertThatIllegalArgumentException().isThrownBy(() -> effortForHaiku.validatedAgainst(ROSTER)).withMessageContaining("ignores the effort flag");
        assertThatIllegalArgumentException().isThrownBy(() -> new CaptainOverride(OPUS_HIGH, " ")).withMessageContaining("captain's reason");
    }

    @Test
    void refusesAPipelineAgentWithoutAWhyOrOnAModelTheRosterRefuses() {
        DispatchRules unexplained = new DispatchRules(List.of(), CATEGORIES, new PipelineAgent(OPUS_HIGH, " "));
        DispatchRules withoutAnEffort = new DispatchRules(List.of(), CATEGORIES, new PipelineAgent(new Route(new ModelId("claude-opus-5-5"), null), "Strong."));

        assertThatIllegalArgumentException().isThrownBy(() -> unexplained.validatedAgainst(ROSTER)).withMessage("The pipeline entry states no why.");
        assertThatIllegalArgumentException().isThrownBy(() -> withoutAnEffort.validatedAgainst(ROSTER))
                .withMessage("The pipeline entry runs claude-opus-5-5 without an effort; name low, medium or high.");
    }
}
