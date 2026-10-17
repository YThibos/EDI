package io.github.ythibos.edi.domain.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.dispatch.ModelRoster.AllowedModel;
import io.github.ythibos.edi.domain.project.ProjectName;

class ResearchFirstTest {

    private static final Route OPUS_HIGH = new Route(new ModelId("claude-opus-5-5"), EffortLevel.HIGH);
    private static final Route SONNET_HIGH = new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.HIGH);
    private static final ModelRoster ROSTER = new ModelRoster(List.of(
            new AllowedModel(new ModelId("claude-opus-5-5"), true),
            new AllowedModel(new ModelId("claude-sonnet-5-5"), true)));
    private static final List<ProjectName> HARNESS = List.of(new ProjectName("harness"));

    @Test
    void queuesResearchForUnplannedWorkAndForABugWhoseCauseIsUnknown() {
        DispatchRules rules = rules(true, true);

        assertThat(rules.researchFirstFor(HARNESS, CategoryId.IMPLEMENTATION_UNPLANNED, false)).isEqualTo(ResearchFirst.QUEUES_RESEARCH);
        assertThat(rules.researchFirstFor(HARNESS, CategoryId.BUGFIX, true)).isEqualTo(ResearchFirst.QUEUES_RESEARCH);
        assertThat(rules.researchFirstFor(HARNESS, CategoryId.BUGFIX, false)).isEqualTo(ResearchFirst.NOT_APPLICABLE);
        assertThat(rules.researchFirstFor(HARNESS, CategoryId.IMPLEMENTATION, false)).isEqualTo(ResearchFirst.NOT_APPLICABLE);
    }

    @Test
    void countsUnplannedWorkForThePilotWhileTheSwitchIsOff() {
        assertThat(rules(false, false).researchFirstFor(HARNESS, CategoryId.IMPLEMENTATION_UNPLANNED, false)).isEqualTo(ResearchFirst.SWITCHED_OFF);
        assertThat(ResearchFirst.SWITCHED_OFF.countsInThePilot()).isTrue();
        assertThat(ResearchFirst.SWITCHED_OFF.queuesResearch()).isFalse();
    }

    @Test
    void leavesAProjectWhoseOverrideRoutesEveryCategoryOutOfThePilot() {
        assertThat(rules(true, true).researchFirstFor(List.of(new ProjectName("EDI")), CategoryId.IMPLEMENTATION_UNPLANNED, false))
                .isEqualTo(ResearchFirst.NOT_APPLICABLE);
    }

    @Test
    void refusesTheSwitchOnACategoryWhoseWorkIsPlannedOrExempt() {
        DispatchRules switchedOnForDocumentation = new DispatchRules(List.of(), List.of(
                new DispatchCategory(CategoryId.RESEARCH, "", OPUS_HIGH, "Plans.", null, false),
                new DispatchCategory(CategoryId.IMPLEMENTATION, "", SONNET_HIGH, "Planned work.", null, false),
                new DispatchCategory(new CategoryId("documentation"), "", SONNET_HIGH, "Prose.", null, true)));

        assertThatIllegalArgumentException().isThrownBy(() -> switchedOnForDocumentation.validatedAgainst(ROSTER))
                .withMessage("The category documentation cannot switch research first on; only implementation-unplanned and bugfix work without a"
                        + " settled plan.");
        assertThat(rules(true, true).validatedAgainst(ROSTER)).isNotNull();
    }

    private static DispatchRules rules(boolean unplannedResearchFirst, boolean bugfixResearchFirst) {
        return new DispatchRules(List.of(new ProjectOverride(new ProjectName("EDI"), OPUS_HIGH, "EDI runs on Opus.")), List.of(
                new DispatchCategory(CategoryId.RESEARCH, "", OPUS_HIGH, "Plans.", null, false),
                new DispatchCategory(CategoryId.IMPLEMENTATION, "", SONNET_HIGH, "Planned work.", null, false),
                new DispatchCategory(CategoryId.IMPLEMENTATION_UNPLANNED, "", OPUS_HIGH, "Unplanned work.", null, unplannedResearchFirst),
                new DispatchCategory(CategoryId.BUGFIX, "", SONNET_HIGH, "Known causes.", null, bugfixResearchFirst)));
    }
}
