package io.github.ythibos.edi.domain.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.branch.BranchKind;
import io.github.ythibos.edi.domain.dispatch.ModelRoster.AllowedModel;
import io.github.ythibos.edi.domain.project.ProjectName;

class DispatchRulesTest {

    private static final Route OPUS_HIGH = new Route(new ModelId("claude-opus-5-5"), EffortLevel.HIGH);
    private static final Route SONNET_MEDIUM = new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.MEDIUM);
    private static final Route HAIKU = new Route(new ModelId("claude-haiku-5-5"), null);
    private static final ModelRoster ROSTER = new ModelRoster(List.of(
            new AllowedModel(new ModelId("claude-opus-5-5"), true),
            new AllowedModel(new ModelId("claude-sonnet-5-5"), true),
            new AllowedModel(new ModelId("claude-haiku-5-5"), false)));
    private static final List<DispatchCategory> CATEGORIES = List.of(
            new DispatchCategory(CategoryId.IMPLEMENTATION, "", SONNET_MEDIUM, "Planned work.", null, false),
            new DispatchCategory(CategoryId.MECHANICAL, "", HAIKU, "Rote work.", BranchKind.CHORE, false));
    private static final PipelineAgent PIPELINE_AGENT = new PipelineAgent(OPUS_HIGH, "The last judge before the captain.");
    private static final DispatchRules RULES = new DispatchRules(
            List.of(new ProjectOverride(new ProjectName("EDI"), Set.of(), OPUS_HIGH, "EDI runs on Opus.")), CATEGORIES, PIPELINE_AGENT);
    private static final List<ProjectName> EDI = List.of(new ProjectName("EDI"));

    @Test
    void routesTheCaptainsOverrideAboveTheProjectOverrideAboveTheCategory() {
        CaptainOverride captain = new CaptainOverride(new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.LOW), "the captain asked");

        assertThat(RULES.routeFor(List.of(new ProjectName("harness")), CategoryId.IMPLEMENTATION, null)).isEqualTo(SONNET_MEDIUM);
        assertThat(RULES.routeFor(EDI, CategoryId.IMPLEMENTATION, null)).isEqualTo(OPUS_HIGH);
        assertThat(RULES.routeFor(EDI, CategoryId.IMPLEMENTATION, captain)).isEqualTo(captain.route());
        assertThatIllegalArgumentException().isThrownBy(() -> RULES.routeFor(EDI, new CategoryId("flying"), null))
                .withMessage("No dispatch category is called flying; the categories are implementation, mechanical.");
    }

    @Test
    void routesAProjectOverrideOnlyForTheCategoriesItCovers() {
        DispatchRules scoped = new DispatchRules(
                List.of(new ProjectOverride(new ProjectName("EDI"), Set.of(CategoryId.IMPLEMENTATION), OPUS_HIGH, "EDI's implementation runs on Opus.")),
                CATEGORIES, PIPELINE_AGENT);

        assertThat(scoped.validatedAgainst(ROSTER)).isSameAs(scoped);
        assertThat(scoped.routeFor(EDI, CategoryId.IMPLEMENTATION, null)).isEqualTo(OPUS_HIGH);
        assertThat(scoped.routeFor(EDI, CategoryId.MECHANICAL, null)).isEqualTo(HAIKU);
        assertThat(RULES.routeFor(EDI, CategoryId.MECHANICAL, null)).isEqualTo(OPUS_HIGH);
    }

    @Test
    void refusesAProjectOverrideThatCoversNoSuchCategory() {
        DispatchRules misspelt = new DispatchRules(
                List.of(new ProjectOverride(new ProjectName("EDI"), Set.of(new CategoryId("implementaton")), OPUS_HIGH, "EDI runs on Opus.")),
                CATEGORIES, PIPELINE_AGENT);

        assertThatIllegalArgumentException().isThrownBy(() -> misspelt.validatedAgainst(ROSTER))
                .withMessage("The override for EDI covers implementaton, which is no dispatch category; the categories are implementation, mechanical.");
    }

    @Test
    void acceptsAModelThatIgnoresTheEffortFlagOnlyWithoutOne() {
        assertThat(RULES.validatedAgainst(ROSTER)).isSameAs(RULES);
        DispatchRules effortForHaiku = new DispatchRules(List.of(),
                List.of(new DispatchCategory(CategoryId.MECHANICAL, "", new Route(new ModelId("claude-haiku-5-5"), EffortLevel.LOW), "Rote.", null, false)),
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
