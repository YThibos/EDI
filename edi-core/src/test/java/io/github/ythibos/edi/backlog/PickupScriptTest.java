package io.github.ythibos.edi.backlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.testing.InstalledPrograms;
import io.github.ythibos.edi.testing.StandInGitHub;
import io.github.ythibos.edi.testing.TestProcesses;

@Tag("adapter")
class PickupScriptTest {

    private static final Path SCRIPT = Path.of(System.getProperty("edi.repository.root", "..")).toAbsolutePath().normalize().resolve("ops/backlog/pickup.mjs");
    private static final String CAPTAIN = "harbour-master";
    private static final String MILESTONES = """
            "milestones": [
              {"number": 1, "title": "The lighthouse", "due_on": "2026-12-18T07:00:00Z", "state": "open"},
              {"number": 2, "title": "The harbour wall", "due_on": "2026-11-13T07:00:00Z", "state": "open"},
              {"number": 3, "title": "The far shore", "due_on": null, "state": "open"}
            ]""";

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    @Test
    void nextNamesOnlyApprovedCloudIssuesWithoutHoldsOrOpenBlockers() throws IOException, InterruptedException {
        StandInGitHub github = serving("""
                "issues": [%s, %s, %s, %s, %s, %s, %s, %s],
                "blockedBy": {"16": [17], "18": [10]},
                "events": {%s}""".formatted(
                issue(10, "EDI-10 The beacon turns", "closed", null, "captain-approved", "cloud-ok"),
                issue(11, "EDI-11 The lamp is trimmed", "open", null, "captain-approved", "cloud-ok"),
                issue(12, "EDI-12 The fog horn sounds", "open", null, "captain-approved", "cloud-ok", "held"),
                issue(13, "EDI-13 The keeper's log is read", "open", null, "captain-approved", "needs-captain-machine"),
                issue(14, "EDI-14 The tide table is posted", "open", null, "cloud-ok"),
                issue(16, "EDI-16 The pier is lit", "open", null, "captain-approved", "cloud-ok"),
                issue(17, "EDI-17 The pier is built", "open", null, "captain-approved", "cloud-ok"),
                issue(18, "EDI-18 The beacon is painted", "open", null, "captain-approved", "cloud-ok"),
                approvals(11, 12, 13, 16, 17, 18)));

        StandInGitHub.Run run = pickup(github, "--next");

        assertThat(run.exitCode()).as(run.all()).isZero();
        assertThat(run.output().lines()).containsExactly(
                "#11 EDI-11 The lamp is trimmed (no milestone) https://github.com/example/edi/issues/11",
                "#17 EDI-17 The pier is built (no milestone) https://github.com/example/edi/issues/17",
                "#18 EDI-18 The beacon is painted (no milestone) https://github.com/example/edi/issues/18");
    }

    @Test
    void nextRefusesAnIssueWhoseApprovalAnotherAccountAdded() throws IOException, InterruptedException {
        StandInGitHub github = serving("""
                "issues": [%s, %s],
                "events": {
                  "21": [{"event": "labeled", "actor": {"login": "a-passer-by"}, "label": {"name": "captain-approved"}}],
                  "22": [{"event": "labeled", "actor": {"login": "%s"}, "label": {"name": "captain-approved"}},
                         {"event": "unlabeled", "actor": {"login": "%s"}, "label": {"name": "captain-approved"}},
                         {"event": "labeled", "actor": {"login": "a-passer-by"}, "label": {"name": "captain-approved"}}]
                }""".formatted(
                issue(21, "EDI-21 The quay is swept", "open", null, "captain-approved", "cloud-ok"),
                issue(22, "EDI-22 The nets are mended", "open", null, "captain-approved", "cloud-ok"),
                CAPTAIN, CAPTAIN));

        StandInGitHub.Run run = pickup(github, "--next");

        assertThat(run.exitCode()).as(run.all()).isZero();
        assertThat(run.output()).startsWith("No open issue may be taken");
        assertThat(run.errors()).contains(
                "#21 is skipped: its captain-approved label was added by a-passer-by, not by the captain's account harbour-master.",
                "#22 is skipped: its captain-approved label was added by a-passer-by, not by the captain's account harbour-master.");
    }

    @Test
    void nextSkipsAnIssueAnOpenPullRequestCloses() throws IOException, InterruptedException {
        StandInGitHub github = serving("""
                "issues": [%s, %s],
                "pulls": [{"number": 40, "title": "EDI-31 The bell is hung", "body": "Delivers the bell.\\n\\nCloses #31"}],
                "events": {%s}""".formatted(
                issue(31, "EDI-31 The bell is hung", "open", null, "captain-approved", "cloud-ok"),
                issue(32, "EDI-32 The bell is rung", "open", null, "captain-approved", "cloud-ok"),
                approvals(31, 32)));

        StandInGitHub.Run run = pickup(github, "--next");

        assertThat(run.output().lines()).containsExactly("#32 EDI-32 The bell is rung (no milestone) https://github.com/example/edi/issues/32");
    }

    @Test
    void nextOrdersByTheMilestonesDueDateThenTheIssueNumber() throws IOException, InterruptedException {
        StandInGitHub github = serving("""
                "issues": [%s, %s, %s, %s, %s],
                "events": {%s}""".formatted(
                issue(51, "EDI-51 No milestone", "open", null, "captain-approved", "cloud-ok"),
                issue(52, "EDI-52 The far shore", "open", 3, "captain-approved", "cloud-ok"),
                issue(53, "EDI-53 The lighthouse", "open", 1, "captain-approved", "cloud-ok"),
                issue(54, "EDI-54 The harbour wall, later", "open", 2, "captain-approved", "cloud-ok"),
                issue(50, "EDI-50 The harbour wall, earlier", "open", 2, "captain-approved", "cloud-ok"),
                approvals(50, 51, 52, 53, 54)));

        StandInGitHub.Run run = pickup(github, "--next");

        assertThat(run.output().lines()).extracting(line -> line.split(" ")[0]).containsExactly("#50", "#54", "#53", "#52", "#51");
        assertThat(run.output()).contains("(The harbour wall, due 2026-11-13)", "(The far shore)");
    }

    @Test
    void theBoardPutsAnIssueAfterItsOpenBlockersAndOnlyReads() throws IOException, InterruptedException {
        StandInGitHub github = serving("""
                "issues": [%s, %s, %s],
                "blockedBy": {"61": [63]},
                "events": {%s}""".formatted(
                issue(61, "EDI-61 The roof is tiled", "open", 2, "captain-approved", "cloud-ok"),
                issue(62, "EDI-62 The door is hung", "open", 1, "captain-approved", "cloud-ok", "held"),
                issue(63, "EDI-63 The walls stand", "open", 1, "captain-approved", "cloud-ok"),
                approvals(61, 62, 63)));

        StandInGitHub.Run run = pickup(github);

        assertThat(run.exitCode()).as(run.all()).isZero();
        assertThat(run.output().lines().skip(2)).containsExactly(
                "| #62 | EDI-62 | The door is hung | The lighthouse, due 2026-12-18 | held | - | - |",
                "| #63 | EDI-63 | The walls stand | The lighthouse, due 2026-12-18 | open | - | - |",
                "| #61 | EDI-61 | The roof is tiled | The harbour wall, due 2026-11-13 | open | #63 | - |");
        assertThat(github.writes()).isEmpty();
    }

    @Test
    void anUnknownArgumentIsRefused() throws IOException, InterruptedException {
        StandInGitHub github = serving("\"issues\": []");

        StandInGitHub.Run run = pickup(github, "--all");

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.errors()).contains("Usage: pickup.mjs [--next] [--repo OWNER/NAME] [--captain LOGIN]");
    }

    private StandInGitHub serving(String world) throws IOException {
        assumeTrue(InstalledPrograms.answers("node", "--version"), "node is not installed");
        return StandInGitHub.serving(directory, """
                {"owner": {"login": "%s", "type": "User"},
                %s,
                %s}""".formatted(CAPTAIN, MILESTONES, world));
    }

    private StandInGitHub.Run pickup(StandInGitHub github, String... arguments) throws IOException, InterruptedException {
        String[] withTheRepository = new String[arguments.length + 2];
        System.arraycopy(arguments, 0, withTheRepository, 0, arguments.length);
        withTheRepository[arguments.length] = "--repo";
        withTheRepository[arguments.length + 1] = "example/edi";
        return github.run(processes, directory, Map.of(), StandInGitHub.node(SCRIPT, withTheRepository));
    }

    private static String issue(int number, String title, String state, Integer milestone, String... labels) {
        return """
                {"number": %d, "id": %d, "title": "%s", "body": "", "state": "%s", "milestone": %s, "labels": [%s]}"""
                .formatted(number, 1000 + number, title, state, milestone, String.join(", ", Arrays.stream(labels).map(label -> "\"" + label + "\"").toList()));
    }

    private static String approvals(int... numbers) {
        return String.join(", ", Arrays.stream(numbers)
                .mapToObj(number -> "\"%d\": [{\"event\": \"labeled\", \"actor\": {\"login\": \"%s\"}, \"label\": {\"name\": \"captain-approved\"}}]".formatted(number, CAPTAIN))
                .toList());
    }
}
