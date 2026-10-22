package io.github.ythibos.edi.application.brief;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
}
