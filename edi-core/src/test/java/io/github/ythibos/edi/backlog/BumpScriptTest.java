package io.github.ythibos.edi.backlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.InstalledPrograms;
import io.github.ythibos.edi.testing.StandInGitHub;
import io.github.ythibos.edi.testing.TestProcesses;

@Tag("adapter")
class BumpScriptTest {

    private static final Path SCRIPT = Path.of(System.getProperty("edi.repository.root", "..")).toAbsolutePath().normalize().resolve("ops/backlog/bump.mjs");
    private static final String FINDING = "The fog horn was skipped again on 24 November.\nThe same cause, the unlit buoy.";

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    @Test
    void aBumpRaisesTheIssueExactlyOneLevelAndCommentsTheFinding() throws IOException, InterruptedException {
        StandInGitHub github = serving(issue(11, "open", "cloud-ok", "priority-4-low"));

        StandInGitHub.Run run = bump(github, FINDING, "11");

        assertThat(run.exitCode()).as(run.all()).isZero();
        assertThat(run.output()).isEqualTo("#11 raised from priority-4-low to priority-3-normal.\n");
        assertThat(github.issue(11).get("labels").toString()).isEqualTo("[\"cloud-ok\",\"priority-3-normal\"]");
        assertThat(github.world().get("comments").get("11").get(0).get("body").asString()).isEqualTo(
                "[EDI session] Raised from priority-4-low to priority-3-normal.\n\n" + FINDING);
    }

    @Test
    void anIssueWithoutAPriorityIsReadAsTheFourthLevelAndRaisedToTheThird() throws IOException, InterruptedException {
        StandInGitHub github = serving(issue(12, "open", "cloud-ok"));

        StandInGitHub.Run run = bump(github, FINDING, "12");

        assertThat(run.output()).isEqualTo("#12 raised from priority-4-low to priority-3-normal.\n");
        assertThat(github.issue(12).get("labels").toString()).isEqualTo("[\"cloud-ok\",\"priority-3-normal\"]");
    }

    @Test
    void everyLevelButTheHighestIsRaisedByOneStepOnly() throws IOException, InterruptedException {
        StandInGitHub github = serving(issue(13, "open", "priority-5-someday"));

        bump(github, FINDING, "13");
        assertThat(github.issue(13).get("labels").toString()).isEqualTo("[\"priority-4-low\"]");
        bump(github, FINDING, "13");
        bump(github, FINDING, "13");
        bump(github, FINDING, "13");

        assertThat(github.issue(13).get("labels").toString()).isEqualTo("[\"priority-1-urgent\"]");
        assertThat(github.world().get("comments").get("13")).hasSize(4);
    }

    @Test
    void anIssueAtTheHighestLevelIsRefusedWithNoComment() throws IOException, InterruptedException {
        StandInGitHub github = serving(issue(14, "open", "priority-1-urgent"));

        StandInGitHub.Run run = bump(github, FINDING, "14");

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.errors()).contains("#14 is already at the highest priority, priority-1-urgent; nothing is raised and nothing is commented.");
        assertThat(github.writes()).isEmpty();
    }

    @Test
    void anIssueWithTwoPriorityLabelsIsRaisedFromTheMoreUrgentAndLeftWithOne() throws IOException, InterruptedException {
        StandInGitHub github = serving(issue(15, "open", "priority-5-someday", "priority-3-normal"));

        StandInGitHub.Run run = bump(github, FINDING, "15");

        assertThat(run.output()).isEqualTo("#15 raised from priority-3-normal to priority-2-high.\n");
        assertThat(github.issue(15).get("labels").toString()).isEqualTo("[\"priority-2-high\"]");
    }

    @Test
    void thePrimaryCommentsUnderItsOwnMarker() throws IOException, InterruptedException {
        StandInGitHub github = serving(issue(16, "open", "priority-4-low"));

        StandInGitHub.Run run = bump(github, FINDING, "--primary", "16");

        assertThat(run.exitCode()).as(run.all()).isZero();
        assertThat(github.world().get("comments").get("16").get(0).get("body").asString()).startsWith("[EDI primary] Raised from");
    }

    @Test
    void anEmptyFindingIsRefusedBeforeAnyChange() throws IOException, InterruptedException {
        StandInGitHub github = serving(issue(17, "open", "priority-4-low"));

        StandInGitHub.Run run = bump(github, "  \n", "17");

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.errors()).contains("The finding that caused the bump is given on standard input, and it is empty.");
        assertThat(github.calls()).isEmpty();
    }

    @Test
    void aClosedIssueIsRefusedBeforeAnyChange() throws IOException, InterruptedException {
        StandInGitHub github = serving(issue(18, "closed", "priority-4-low"));

        StandInGitHub.Run run = bump(github, FINDING, "18");

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.errors()).contains("#18 is closed; only an open issue is raised.");
        assertThat(github.writes()).isEmpty();
    }

    @Test
    void anUnknownIssueOrAMissingNumberIsRefused() throws IOException, InterruptedException {
        StandInGitHub github = serving(issue(19, "open", "priority-4-low"));

        StandInGitHub.Run unknown = bump(github, FINDING, "404");
        StandInGitHub.Run missing = bump(github, FINDING);

        assertThat(unknown.exitCode()).isEqualTo(1);
        assertThat(unknown.errors()).contains("no issue #404");
        assertThat(missing.exitCode()).isEqualTo(1);
        assertThat(missing.errors()).contains("Usage: bump.mjs [--primary] [--repo OWNER/NAME] ISSUE < finding");
        assertThat(github.writes()).isEmpty();
    }

    @Test
    void aBumpGoesToTheUpstreamOfTheEdiCloneWhicheverCheckoutItRunsIn() throws IOException, InterruptedException {
        StandInGitHub github = serving(issue(20, "open", "priority-4-low"));

        StandInGitHub.Run run = bump(github, FINDING, "20");

        assertThat(run.exitCode()).as(run.all()).isZero();
        assertThat(github.calls()).isNotEmpty().allSatisfy(call -> assertThat(call).contains(" repos/example/edi/issues/20"));
    }

    @Test
    void aSessionWithOnlyTheRepositoryNamesItWithRepo() throws IOException, InterruptedException {
        StandInGitHub github = serving(issue(21, "open", "priority-4-low"));

        StandInGitHub.Run run = bump(github, FINDING, "--repo", "harbour-office/beacons", "21");

        assertThat(run.exitCode()).as(run.all()).isZero();
        assertThat(github.calls()).isNotEmpty().allSatisfy(call -> assertThat(call).contains(" repos/harbour-office/beacons/issues/21"));
    }

    @Test
    void aRefusedRelabelLeavesNoCommentSayingTheIssueWasRaised() throws IOException, InterruptedException {
        StandInGitHub github = StandInGitHub.serving(directory, """
                {"owner": {"login": "harbour-master", "type": "User"}, "labelWritesRefused": true,
                "issues": [%s]}""".formatted(issue(22, "open", "priority-4-low")));

        StandInGitHub.Run run = bump(github, FINDING, "22");

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.errors()).contains("HTTP 403");
        assertThat(github.world().get("comments").get("22")).isNull();
        assertThat(github.issue(22).get("labels").toString()).isEqualTo("[\"priority-4-low\"]");
    }

    @BeforeEach
    void anEdiHomeWhoseCloneNamesTheUpstreamBesideAnotherProjectsCheckout() throws IOException {
        assumeTrue(InstalledPrograms.answers("node", "--version"), "node is not installed");
        FixtureRepository.initialise(Files.createDirectories(directory.resolve("home/projects/EDI"))).configure("remote.origin.url", "https://github.com/example/edi");
        FixtureRepository.initialise(Files.createDirectories(directory.resolve("lighthouse"))).configure("remote.origin.url", "https://github.com/lighthouse-keepers/fog-horn");
    }

    private StandInGitHub serving(String issue) throws IOException {
        return StandInGitHub.serving(directory, """
                {"owner": {"login": "harbour-master", "type": "User"},
                "issues": [%s]}""".formatted(issue));
    }

    private StandInGitHub.Run bump(StandInGitHub github, String finding, String... arguments) throws IOException, InterruptedException {
        return github.run(processes, directory.resolve("lighthouse"), Map.of(
                "EDI_HOME", directory.resolve("home").toString(),
                "GIT_CONFIG_GLOBAL", "/dev/null",
                "GIT_CONFIG_NOSYSTEM", "1"), StandInGitHub.node(SCRIPT, arguments), finding);
    }

    private static String issue(int number, String state, String... labels) {
        return """
                {"number": %d, "id": %d, "title": "EDI-%d The fog horn sounds", "body": "", "state": "%s", "milestone": null, "labels": [%s]}"""
                .formatted(number, 1000 + number, number, state, String.join(", ", Arrays.stream(labels).map(label -> "\"" + label + "\"").toList()));
    }
}
