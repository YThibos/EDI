package io.github.ythibos.edi.domain.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

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
    private static final DispatchRules RULES = new DispatchRules(
            List.of(new ProjectOverride(new ProjectName("EDI"), OPUS_HIGH, "EDI runs on Opus.")),
            List.of(
                    new DispatchCategory(CategoryId.IMPLEMENTATION, "", SONNET_MEDIUM, "Planned work.", null, false),
                    new DispatchCategory(CategoryId.MECHANICAL, "", HAIKU, "Rote work.", BranchKind.CHORE, false)));

    @Test
    void routesTheCaptainsOverrideAboveTheProjectOverrideAboveTheCategory() {
        CaptainOverride captain = new CaptainOverride(new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.LOW), "the captain asked");

        assertThat(RULES.routeFor(List.of(new ProjectName("harness")), CategoryId.IMPLEMENTATION, null)).isEqualTo(SONNET_MEDIUM);
        assertThat(RULES.routeFor(List.of(new ProjectName("EDI")), CategoryId.IMPLEMENTATION, null)).isEqualTo(OPUS_HIGH);
        assertThat(RULES.routeFor(List.of(new ProjectName("EDI")), CategoryId.IMPLEMENTATION, captain)).isEqualTo(captain.route());
        assertThatIllegalArgumentException().isThrownBy(() -> RULES.routeFor(List.of(new ProjectName("EDI")), new CategoryId("flying"), null))
                .withMessage("No dispatch category is called flying; the categories are implementation, mechanical.");
    }

    @Test
    void acceptsAModelThatIgnoresTheEffortFlagOnlyWithoutOne() {
        assertThat(RULES.validatedAgainst(ROSTER)).isSameAs(RULES);
        DispatchRules effortForHaiku = new DispatchRules(List.of(),
                List.of(new DispatchCategory(CategoryId.MECHANICAL, "", new Route(new ModelId("claude-haiku-5-5"), EffortLevel.LOW), "Rote.", null, false)));

        assertThatIllegalArgumentException().isThrownBy(() -> effortForHaiku.validatedAgainst(ROSTER)).withMessageContaining("ignores the effort flag");
        assertThatIllegalArgumentException().isThrownBy(() -> new CaptainOverride(OPUS_HIGH, " ")).withMessageContaining("captain's reason");
    }
}
