package io.github.ythibos.edi.application.brief;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.testing.BriefFixtures;

class WorkerBriefsTest {

    private static final String PLAN = "Follow the plan that research task `plan-lamp` settled; its report follows verbatim.\n\n1. Fit the brass lamp.";

    private final WorkerBriefs briefs = new WorkerBriefs();

    @Test
    void putsThePlanOfAPlannedShipBeforeItsOwnBuildInstructions() {
        String brief = briefs.briefFor(BriefFixtures.shipFollowing(PLAN, "Keep the old bracket.", false));

        assertThat(brief).contains("## Spec\n\n" + PLAN + "\n\nKeep the old bracket.\n\n## Project rules");
        assertThat(briefs.briefFor(BriefFixtures.shipFollowing(PLAN, "", false))).contains("## Spec\n\n" + PLAN + "\n\n## Project rules");
    }

    @Test
    void givesThePipelineThePlanAndBuildInstructionsOfAPlannedShipAfterTheCaptainsIntent() {
        String intent = briefs.pipelineIntentFor(BriefFixtures.shipFollowing(PLAN, "Keep the old bracket.", false)).text();

        assertThat(intent).startsWith("## Captain's intent\n\nFit the lamp in the hall.\n\n## Spec\n\n" + PLAN + "\n\nKeep the old bracket.\n\n");
    }

    @Test
    void asksForTheWorkedLineOnlyWhileTheMachineProfileRequiresIt() {
        String worked = "end `/home/developer/.edi/data/tasks/fit-lamp/report.md` with the line `worked: <start> -> <end> = <H>h<MM>m`";

        assertThat(briefs.briefFor(BriefFixtures.shipFollowing(null, "", true))).contains("Before you report done, " + worked);
        assertThat(briefs.briefFor(BriefFixtures.shipFollowing(null, "", false))).doesNotContain("worked:");
    }

    @Test
    void tellsAScoutToPassItsBoardsDecisionCardWhenItArmsTheBoard() {
        String brief = briefs.briefFor(BriefFixtures.scout());

        assertThat(brief).contains("`edi board arm <board.html> --task fit-lamp --link <url> --questions <n>`",
                "A decision the board asks is a card you pass with `--card <card.json>`",
                "EDI raises the card on your task and binds it before the captain sees the board");
        assertThat(briefs.briefFor(BriefFixtures.shipFollowing(null, "", false))).doesNotContain("edi board arm");
    }

    @Test
    void refusesABriefPastItsBudgetNamingItsLongestSection() {
        int specLinesWithinTheBudget = specLinesThatFillTheBudget();

        assertThatCode(() -> briefs.requireWithinItsLength(BriefFixtures.shipFollowing(null, buildInstructions(specLinesWithinTheBudget), false)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> briefs.requireWithinItsLength(BriefFixtures.shipFollowing(null, buildInstructions(specLinesWithinTheBudget + 1), false)))
                .isInstanceOf(Refusal.class)
                .hasMessage("Counting the captain's intent as one line and leaving out any research plan, the brief would run to 121 lines, over the"
                        + " 120 a brief holds; its longest section is Spec, at " + (specLinesWithinTheBudget + 4)
                        + " lines: shorten it, or point the worker to a file in the task directory.");
    }

    @Test
    void leavesTheResearchPlanItCarriesOutOfTheBriefsLength() {
        String longPlan = PLAN + "\n" + "2. Polish the brass.\n".repeat(200);

        assertThatCode(() -> briefs.requireWithinItsLength(BriefFixtures.shipFollowing(longPlan, "Keep the old bracket.", false))).doesNotThrowAnyException();
        assertThatThrownBy(() -> briefs.requireWithinItsLength(BriefFixtures.shipFollowing(longPlan, buildInstructions(200), false)))
                .isInstanceOf(Refusal.class).hasMessageContaining("its longest section is Spec, at 203 lines");
    }

    @Test
    void leavesTheCaptainsIntentOutOfTheBriefsLength() {
        String longIntent = "The captain's words, one per line.\n".repeat(400);

        assertThatCode(() -> briefs.requireWithinItsLength(BriefFixtures.largestShip(longIntent, "Rename Lamp to Lantern."))).doesNotThrowAnyException();
    }

    @Test
    void attributesLinesToTheTemplatesSectionsWhateverHeadingsTheSpecHolds() {
        String spec = "## Reporting\n\n" + buildInstructions(200);

        assertThatThrownBy(() -> briefs.requireWithinItsLength(BriefFixtures.shipFollowing(null, spec, false)))
                .isInstanceOf(Refusal.class).hasMessageContaining("its longest section is Spec, at 205 lines");
    }

    private int specLinesThatFillTheBudget() {
        return 120 - (int) briefs.briefFor(BriefFixtures.shipFollowing(null, buildInstructions(1), false)).lines().count() + 1;
    }

    private static String buildInstructions(int lines) {
        return "Build instruction.\n".repeat(lines).strip();
    }
}
