package io.github.ythibos.edi.backlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
class MigrationScriptTest {

    private static final Path SCRIPT = Path.of(System.getProperty("edi.repository.root", "..")).toAbsolutePath().normalize().resolve("ops/backlog/migrate.mjs");
    private static final String WORLD = """
            {"owner": {"login": "harbour-master", "type": "User"},
             "labels": [{"name": "cloud-ok", "description": "A cloud session may take it"}, {"name": "parked", "description": ""},
                        {"name": "wave-1", "description": ""}, {"name": "wave-2", "description": ""}],
             "issues": [
               {"number": 3, "id": 1003, "title": "EDI-85 The stage is stored", "body": "The spec is elsewhere.", "state": "open", "milestone": null, "labels": ["cloud-ok", "wave-1"]},
               {"number": 4, "id": 1004, "title": "EDI-86 Delivered, changing, ready", "body": "", "state": "open", "milestone": null, "labels": ["cloud-ok", "wave-2"]},
               {"number": 5, "id": 1005, "title": "EDI-105 The validation pipeline", "body": "", "state": "open", "milestone": null, "labels": ["cloud-ok", "parked"]},
               {"number": 6, "id": 1006, "title": "EDI-144 Validations are recorded", "body": "", "state": "open", "milestone": null, "labels": ["cloud-ok", "parked"]},
               {"number": 7, "id": 1007, "title": "EDI-62 The Week view", "body": "", "state": "open", "milestone": null, "labels": ["cloud-ok"]},
               {"number": 8, "id": 1008, "title": "EDI-97 Effort per task", "body": "", "state": "open", "milestone": null, "labels": ["cloud-ok"]},
               {"number": 9, "id": 1009, "title": "EDI-1 Clear the advisory", "body": "", "state": "open", "milestone": null, "labels": ["needs-captain-machine"]},
               {"number": 10, "id": 1010, "title": "EDI-94 Worklogs through the tracker", "body": "", "state": "open", "milestone": null, "labels": ["cloud-ok"]}
             ]}""";
    private static final String PLAN = """
            {"milestones": [
               {"title": "The lighthouse", "due": "2026-12-18", "description": "The lighthouse is built."},
               {"title": "The far shore", "due": null, "description": "The far shore is reached."}],
             "items": {
               "EDI-85": {"milestone": "The lighthouse"},
               "EDI-86": {"milestone": "The lighthouse"},
               "EDI-105": {"milestone": "The far shore"},
               "EDI-144": {"milestone": "The far shore", "parent": "EDI-105"},
               "EDI-62": {"milestone": null},
               "EDI-97": {"milestone": null}},
             "security": ["EDI-62"],
             "marker": {"title": "A week of use of the Week view", "blockedBy": ["EDI-62"], "blocks": ["EDI-97"], "body": "A marker, not work.\\n"}}""";

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    private FixtureRepository checkout;
    private StandInGitHub github;
    private Path plan;

    @BeforeEach
    void aCheckoutWhoseMainHoldsTheItems() throws IOException {
        assumeTrue(InstalledPrograms.answers("node", "--version"), "node is not installed");
        checkout = FixtureRepository.initialise(Files.createDirectories(directory.resolve("checkout")))
                .write("docs/backlog/EDI-85.md", item("EDI-85", "The stage is stored", "-", "open", "#3", """
                        ## Goal

                        The stage is stored, as [the plan](research/EDI-74-plan.md#step-3) and [the record](../decisions/stages.md) say.

                        ## Delivery

                        Deliver this item as the [pickup protocol](README.md#pickup-protocol) says.
                        """))
                .write("docs/backlog/EDI-86.md", item("EDI-86", "Delivered, changing, ready", "[EDI-85](EDI-85.md), [EDI-81](EDI-81.md)", "open", "#4", """
                        ## Goal

                        It reads the stage of [EDI-85](EDI-85.md), as [EDI-81](EDI-81.md) did.

                        ```text
                        [kept](EDI-85.md)
                        ```
                        """))
                .write("docs/backlog/EDI-105.md", item("EDI-105", "The validation pipeline", "-", "parked: until EDI-89 lands.", "#5", "## Goal\n\nValidate.\n"))
                .write("docs/backlog/EDI-144.md", item("EDI-144", "Validations are recorded", "[EDI-105](EDI-105.md)", "parked: until EDI-105 releases the slices", "#6",
                        "## Goal\n\nRecord.\n"))
                .write("docs/backlog/EDI-62.md", item("EDI-62", "The Week view", "-", "open", "#7", "## Goal\n\nShow the week.\n"))
                .write("docs/backlog/EDI-97.md", item("EDI-97", "Effort per task", "[EDI-62](EDI-62.md)", "needs-captain-machine: it reads the store.", "#8",
                        "## Goal\n\nMeasure.\n"))
                .write("docs/backlog/EDI-81.md", item("EDI-81", "Learnings name their ticket", "-", "delivered", "-", "## Goal\n\nName.\n"))
                .write("docs/backlog/EDI-1.md", item("EDI-1", "Clear the advisory", "-", "delivered", "#9", "## Goal\n\nClear.\n"))
                .write("docs/backlog/EDI-94.md", item("EDI-94", "Worklogs through the tracker", "-", "folded: into [EDI-86](EDI-86.md), on the captain's word", "#10",
                        "## Goal\n\nBook.\n"))
                .commit("EDI-117 Add the backlog");
        github = StandInGitHub.serving(directory, WORLD);
        plan = Files.writeString(directory.resolve("plan.json"), PLAN);
    }

    @Test
    void aDryRunNamesEveryChangeAndMakesNone() throws IOException, InterruptedException {
        StandInGitHub.Run run = migrate("--dry-run");

        assertThat(run.exitCode()).as(run.all()).isZero();
        assertThat(run.output()).contains(
                "would rename the label parked to held, so its issues keep it",
                "would create the label captain-approved",
                "would create the milestone \"The lighthouse\", due 2026-12-18",
                "would create the milestone \"The far shore\", with no due date",
                "would open the marker issue \"A week of use of the Week view\" labelled held, needs-captain-machine:",
                "would update #3 EDI-85: milestone \"The lighthouse\", body",
                "would make #6 EDI-144 a sub-issue of #5 EDI-105",
                "would mark #4 EDI-86 blocked by #3 EDI-85",
                "#4 EDI-86 keeps EDI-81 as text: EDI-81 has no issue",
                "would mark #8 EDI-97 blocked by A week of use of the Week view",
                "would close #9 EDI-1 as completed, since its file says delivered",
                "would close #10 EDI-94 as not planned, since its file says folded",
                "would remove the label wave-1 from the repository");
        assertThat(run.output()).doesNotContain("captain-approved,").doesNotContain("label #3 EDI-85 captain-approved");
        assertThat(github.writes()).isEmpty();
    }

    @Test
    void aRunMovesEachSpecOntoItsIssueWithItsLinksRewritten() throws IOException, InterruptedException {
        StandInGitHub.Run run = migrate();

        assertThat(run.exitCode()).as(run.all()).isZero();
        String commit = checkout.head();
        assertThat(github.issue(3).get("body").asString()).isEqualTo("""
                | Field | Value |
                |---|---|
                | Kind | ship, a change to the repository, on branch `feature/EDI-85` |
                | Source | The captain's word |
                | Size | M |

                ## Goal

                The stage is stored, as [the plan](https://github.com/example/edi/blob/main/docs/research/EDI-74-plan.md#step-3) \
                and [the record](https://github.com/example/edi/blob/main/docs/decisions/stages.md) say.

                The spec moved here from [docs/backlog/EDI-85.md](https://github.com/example/edi/blob/%s/docs/backlog/EDI-85.md).
                """.formatted(commit));
        assertThat(github.issue(4).get("body").asString()).contains(
                "It reads the stage of [EDI-85](https://github.com/example/edi/issues/3), as [EDI-81](https://github.com/example/edi/blob/%s/docs/backlog/EDI-81.md) did."
                        .formatted(commit),
                "```text\n[kept](EDI-85.md)\n```");
        assertThat(github.issue(6).get("body").asString()).contains("\n## Held\n\nUntil EDI-105 releases the slices\n");
        assertThat(github.issue(8).get("body").asString()).contains("\n## Needs the captain's machine\n\nIt reads the store.\n");
    }

    @Test
    void aRunSetsTheMilestonesParentsBlockersAndLabels() throws IOException, InterruptedException {
        StandInGitHub.Run run = migrate();

        assertThat(run.exitCode()).as(run.all()).isZero();
        assertThat(github.world().get("milestones").toString()).contains("\"title\":\"The lighthouse\",\"due_on\":\"2026-12-18T12:00:00Z\"");
        assertThat(github.issue(3).get("milestone").asInt()).isEqualTo(1);
        assertThat(github.issue(5).get("milestone").asInt()).isEqualTo(2);
        assertThat(github.issue(7).get("milestone").isNull()).isTrue();
        assertThat(github.world().get("subIssues").get("5").toString()).isEqualTo("[6]");
        assertThat(github.world().get("blockedBy").get("4").toString()).isEqualTo("[3]");
        assertThat(github.world().get("blockedBy").get("6").toString()).isEqualTo("[5]");
        assertThat(github.world().get("blockedBy").get("8").toString()).isEqualTo("[7,11]");
        assertThat(github.world().get("blockedBy").get("11").toString()).isEqualTo("[7]");
        assertThat(github.issue(11).get("title").asString()).isEqualTo("A week of use of the Week view");
        assertThat(github.issue(11).get("labels").toString()).isEqualTo("[\"held\",\"needs-captain-machine\"]");
        assertThat(github.issue(5).get("labels").toString()).isEqualTo("[\"cloud-ok\",\"held\"]");
        assertThat(github.issue(7).get("labels").toString()).isEqualTo("[\"cloud-ok\",\"security\"]");
        assertThat(github.issue(8).get("labels").toString()).isEqualTo("[\"needs-captain-machine\"]");
        assertThat(github.world().get("labels").toString()).doesNotContain("wave-").doesNotContain("\"parked\"").contains("captain-approved");
        assertThat(github.world().toString()).doesNotContain("\"captain-approved\"]");
    }

    @Test
    void aRunSettlesTheDriftedIssues() throws IOException, InterruptedException {
        StandInGitHub.Run run = migrate();

        assertThat(run.exitCode()).as(run.all()).isZero();
        assertThat(github.issue(9).get("state").asString()).isEqualTo("closed");
        assertThat(github.issue(9).get("state_reason").asString()).isEqualTo("completed");
        assertThat(github.issue(10).get("state_reason").asString()).isEqualTo("not_planned");
        assertThat(github.world().get("comments").get("10").get(0).get("body").asString())
                .isEqualTo("[EDI session] EDI-94 was folded into EDI-86 (#4), which carries its criteria, so this issue closes as not planned.");
    }

    @Test
    void theCaptainsApprovalIsAddedOnlyWithHisWord() throws IOException, InterruptedException {
        StandInGitHub.Run run = migrate("--approve", "the captain's word of 4 November 2026");

        assertThat(run.exitCode()).as(run.all()).isZero();
        assertThat(run.output()).contains("Label #3 EDI-85 captain-approved, on the captain's word of 4 November 2026");
        assertThat(github.issue(3).get("labels").toString()).contains("captain-approved");
        assertThat(github.issue(11).get("labels").toString()).doesNotContain("captain-approved");
    }

    @Test
    void aRerunChangesNothing() throws IOException, InterruptedException {
        migrate("--approve", "the captain's word of 4 November 2026");
        Files.delete(directory.resolve("github-state/calls"));

        StandInGitHub.Run rerun = migrate();

        assertThat(rerun.exitCode()).as(rerun.all()).isZero();
        assertThat(rerun.output().strip()).endsWith("Nothing to change.");
        assertThat(github.writes()).isEmpty();
    }

    @Test
    void anItemThePlanDoesNotPlaceIsRefusedBeforeAnyChange() throws IOException, InterruptedException {
        checkout.write("docs/backlog/EDI-170.md", item("EDI-170", "The guard reads the whole diff", "-", "open", "#12", "## Goal\n\nRead.\n")).commit("EDI-170 Add it");

        StandInGitHub.Run run = migrate();

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.errors()).contains("The plan names no milestone for EDI-170");
        assertThat(github.writes()).isEmpty();
    }

    private StandInGitHub.Run migrate(String... arguments) throws IOException, InterruptedException {
        String[] all = new String[arguments.length + 6];
        System.arraycopy(new String[] {"--from", "main", "--plan", plan.toString(), "--repo", "example/edi"}, 0, all, 0, 6);
        System.arraycopy(arguments, 0, all, 6, arguments.length);
        return github.run(processes, checkout.root(), Map.of("GIT_CONFIG_GLOBAL", "/dev/null", "GIT_CONFIG_NOSYSTEM", "1"), StandInGitHub.node(SCRIPT, all));
    }

    private static String item(String key, String title, String blockedBy, String status, String issue, String spec) {
        String linkedIssue = issue.equals("-") ? "-" : "[%s](https://github.com/example/edi/issues/%s)".formatted(issue, issue.substring(1));
        return """
                # %s %s

                | Field | Value |
                |---|---|
                | Key | %s |
                | Kind | ship, a change to the repository, on branch `feature/%s` |
                | Wave and order | wave 2, order 5 |
                | Source | The captain's word |
                | Size | M |
                | Blocked by | %s |
                | Status | %s |
                | Issue | %s |
                | Cloud session | ok |

                %s""".formatted(key, title, key, key, blockedBy, status, linkedIssue, spec);
    }
}
