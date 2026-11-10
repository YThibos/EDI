package io.github.ythibos.edi.application.brief;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.DeliveryMode;
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
    void leavesTheFullBuildAndTheNativeBuildToCiWhereTheProjectsCiRuns() {
        String brief = briefs.briefFor(BriefFixtures.shipWhereCi(true));

        assertThat(brief).contains("## Validation and delivery\n\n"
                + "Leave the full build to CI: locally, run the tests the change touches and the change's own end-to-end checks, and neither"
                + " `./mvnw -B verify` nor `./mvnw -B -Pnative,quick-build verify`.\n"
                + "Push early: run the pipeline as soon as those checks pass, so CI starts on the merge request; its run on your last commit is the"
                + " full-build evidence.\n"
                + "Run a wider local build only to diagnose a CI failure.\n"
                + "The merge request body names the CI run it rests on and the targeted checks run locally, and never says a step passed that was not"
                + " run.\n\n"
                + "Commit on `feature/HRN-3`")
                .doesNotContain("Run the local native build", "local evidence", "local verification");
    }

    @Test
    void leavesTheFullBuildToCiWhereTheProjectsCiRunsAndItBuildsNoNativeImage() {
        String brief = briefs.briefFor(BriefFixtures.shipWithoutANativeImageWhereCi(true));

        assertThat(brief).contains("## Validation and delivery\n\n"
                + "Leave the full build to CI: locally, run the tests the change touches and the change's own end-to-end checks, and not"
                + " `./mvnw -B verify`.\n"
                + "Push early:")
                .contains("Run a wider local build only to diagnose a CI failure.\n")
                .doesNotContain("native", "local verification");
    }

    @Test
    void tellsAMechanicalWorkerThatEdiRunsTheVerifyCommandItselfWhereTheProjectsCiRuns() {
        String brief = briefs.briefFor(BriefFixtures.largestShip("Rename the lamp.", "Rename Lamp to Lantern."));

        assertThat(brief).contains("and neither `./mvnw -B verify` nor `./mvnw -B -Pnative,quick-build verify`.\n"
                + "EDI runs `./mvnw -B verify` itself when it checks the done of this mechanical task, so you do not run it.\n"
                + "Push early:")
                .contains("EDI accepts the done only when the project's verify command, which EDI runs itself, passes and the change touches only the"
                        + " named files;");
        assertThat(briefs.briefFor(BriefFixtures.shipWhereCi(true))).doesNotContain("EDI runs", "mechanical task");
    }

    @Test
    void asksForTheLocalNativeBuildOnlyWhenTheChangeCanReachTheNativeImageWhereTheProjectsCiDoesNotRun() {
        String brief = briefs.briefFor(BriefFixtures.shipWhereCi(false));

        assertThat(brief).contains("## Validation and delivery\n\n"
                + "Run the local native build `./mvnw -B -Pnative,quick-build verify` only when the change can reach the native image: it touches"
                + " reflection or serialisation-bound types, resources the image reads, Spring configuration, native-image configuration, dependencies"
                + " or build files.\n"
                + "Otherwise `./mvnw -B verify` is the local evidence: the merge request body says the native build was not needed and why.\n\n"
                + "Commit on `feature/HRN-3`")
                .doesNotContain("Leave the full build to CI", "left to CI", "Run a wider local build");
    }

    @Test
    void asksForTheLocalVerificationAloneWhereTheProjectsCiDoesNotRunAndItBuildsNoNativeImage() {
        String brief = briefs.briefFor(BriefFixtures.shipWithoutANativeImageWhereCi(false));

        assertThat(brief).contains("## Validation and delivery\n\nCommit on `feature/HRN-3`")
                .contains("## Definition of done\n\nYour last commit passes the local verification (`./mvnw -B verify`), and")
                .doesNotContain("native", "Leave the full build to CI", "Run a wider local build");
    }

    @Test
    void runsThePipelineWithoutItsRebaseStepAndWithoutItsCiStepWhereTheProjectsCiDoesNotRun() {
        String intent = " --intent \"$(cat '/home/developer/.edi/data/tasks/fit-lamp/intent.md')\"";

        assertThat(briefs.briefFor(BriefFixtures.shipWhereCi(true))).contains("no-mistakes axi run --base-branch main --skip rebase" + intent);
        assertThat(briefs.briefFor(BriefFixtures.shipWhereCi(false))).contains("no-mistakes axi run --base-branch main --skip rebase,ci" + intent);
    }

    @Test
    void tellsTheWorkerToCatchUpWithTheBaseByAMergeCommitBeforeEachPipelineRun() {
        String brief = briefs.briefFor(BriefFixtures.shipWhereCi(true));

        assertThat(brief).contains("```\n\nBefore each run, fetch, and when the branch is behind `origin/main` run"
                + " `git merge --no-ff -m \"HRN-3 Merge origin/main into feature/HRN-3\" origin/main`; never rebase.\n");
    }

    @Test
    void marksTheMergeRequestReadyForReviewOnceThePipelinePassesWhereTheProjectsCiDoesNotRun() {
        String draft = "The pipeline pushes the branch and opens the merge request as a draft; mark it ready for review once ";

        assertThat(briefs.briefFor(BriefFixtures.shipWhereCi(true))).contains(draft + "its checks are green.\n");
        assertThat(briefs.briefFor(BriefFixtures.shipWhereCi(false))).contains(draft + "the pipeline passes.\n").doesNotContain("checks are green");
    }

    @Test
    void takesTheLocalVerificationAsTheEvidenceOfDoneWhereTheProjectsCiDoesNotRun() {
        String report = "`/home/developer/.edi/data/tasks/fit-lamp/report.md`";

        assertThat(briefs.briefFor(BriefFixtures.shipWhereCi(true)))
                .contains("## Definition of done\n\nCI is green on the merge request for your last commit, and " + report + " holds a short delivery note.\n");
        assertThat(briefs.briefFor(BriefFixtures.shipWhereCi(false)))
                .contains("## Definition of done\n\nYour last commit passes the local verification (`./mvnw -B verify`, plus"
                        + " `./mvnw -B -Pnative,quick-build verify` when the change can reach the native image), and " + report
                        + " holds a short delivery note with that evidence.\n")
                .doesNotContain("CI is green");
    }

    @Test
    void asksForTheReasonTheNativeBuildWasSkippedInTheReportWhenNoMergeRequestCarriesIt() {
        String brief = briefs.briefFor(BriefFixtures.shipBuildingANativeImage(DeliveryMode.REPORT));

        assertThat(brief).contains("Otherwise `./mvnw -B verify` is the local evidence:"
                + " `/home/developer/.edi/data/tasks/fit-lamp/report.md` says the native build was not needed and why.\n\n"
                + "This work is delivered by its report")
                .doesNotContain("merge request body", "Leave the full build to CI");
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
