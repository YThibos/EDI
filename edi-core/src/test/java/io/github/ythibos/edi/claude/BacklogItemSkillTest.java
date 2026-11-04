package io.github.ythibos.edi.claude;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.InstalledPrograms;
import io.github.ythibos.edi.testing.StandInGitHub;
import io.github.ythibos.edi.testing.StandInScripts;
import io.github.ythibos.edi.testing.TestProcesses;

@Tag("adapter")
class BacklogItemSkillTest {

    private static final Path SCRIPT = Path.of(System.getProperty("edi.repository.root", "..")).toAbsolutePath().normalize()
            .resolve("claude/skills/edi-backlog-item/backlog-item.mjs");
    private static final String WORLD = """
            {"owner": {"login": "harbour-master", "type": "User"},
             "labels": [{"name": "cloud-ok"}, {"name": "held"}],
             "milestones": [{"number": 1, "title": "The lighthouse", "due_on": "2026-12-18T07:00:00Z", "state": "open"}],
             "issues": [
               {"number": 3, "id": 1003, "title": "EDI-85 The stage is stored", "body": "", "state": "open", "milestone": 1, "labels": ["cloud-ok"]},
               {"number": 5, "id": 1005, "title": "EDI-105 The validation pipeline", "body": "", "state": "open", "milestone": null, "labels": ["held"]}
             ],
             "pulls": [{"number": 8, "title": "EDI-99 Something else", "body": ""}]}""";
    private static final String SPEC = """
            ## Goal

            The harbour lamps light themselves at dusk.

            ## Context

            A keeper lights them by hand.

            ## Acceptance criteria

            - Each lamp lights at dusk.

            ## Requirements from decisions and answers

            - The captain asked for it.

            ## Read first

            - AGENTS.md.

            ## How to verify

            - `./mvnw verify`.
            """;
    private static final String DRAFT = """
            ---
            title: Harbour lamps light themselves at dusk
            source: The captain's word of 29 October 2026
            size: S
            milestone: The lighthouse
            parent: EDI-105
            blocked-by: EDI-85
            status: open
            ---
            """ + SPEC;
    private static final String STAND_IN_EDI = """
            #!/bin/sh
            echo "edi $*" >> "$STATE/edi-calls"
            case "$1 $2" in
              "task list") if [ -f "$STATE/task" ]; then echo '{"tasks":[{"id":"harbour-lamps","intake":{"title":"Harbour lamps light themselves at dusk","projects":["EDI"]}}]}'; else echo '{"tasks":[]}'; fi ;;
              "task add") cat > "$STATE/task"; if [ -f "$STATE/added" ]; then cat "$STATE/added"; else echo "Queued harbour-lamps on $(echo "$*" | sed 's/.*--key \\([^ ]*\\).*/\\1/')."; fi ;;
              "task show") hold=null; if [ -f "$STATE/hold" ]; then hold=$(printf '{"kind":"deferral","reason":"%s","until":null}' "$(cat "$STATE/hold")"); fi
                printf '{"task":{"id":"harbour-lamps","intake":{"kind":"ship","title":"Harbour lamps light themselves at dusk","projects":["EDI"],"workKey":"EDI-9"},"notes":"","hold":%s}}' "$hold" ;;
            esac
            if [ "$1" = hold ]; then printf '%s' "$6" > "$STATE/hold"; fi
            """;

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    private StandInGitHub github;
    private Path state;
    private Path draft;

    @BeforeEach
    void anEdiHomeWhoseCloneNamesTheUpstream() throws IOException {
        assumeTrue(InstalledPrograms.answers("node", "--version"), "node is not installed");
        FixtureRepository.initialise(Files.createDirectories(directory.resolve("home/projects/EDI"))).configure("remote.origin.url", "https://github.com/example/edi");
        github = StandInGitHub.serving(directory, WORLD);
        state = directory.resolve("github-state");
        StandInScripts.installed(github.bin().resolve("edi"), STAND_IN_EDI);
        draft = Files.writeString(directory.resolve("harbour-lamps.md"), DRAFT);
    }

    @Test
    void aRunOpensTheIssueKeyedByItsNumberWithItsMilestoneParentAndBlockers() throws IOException, InterruptedException {
        StandInGitHub.Run run = backlogItem(draft.toString());

        assertThat(run.exitCode()).as(run.all()).isZero();
        assertThat(github.issue(9).get("title").asString()).isEqualTo("EDI-9 Harbour lamps light themselves at dusk");
        assertThat(github.issue(9).get("milestone").asInt()).isEqualTo(1);
        assertThat(github.issue(9).get("labels").toString()).isEqualTo("[\"cloud-ok\"]");
        assertThat(github.issue(9).get("body").asString()).startsWith("""
                | Field | Value |
                |---|---|
                | Kind | ship, a change to the repository, on a `feature/` branch named by its key |
                | Source | The captain's word of 29 October 2026 |
                | Size | S |

                ## Goal
                """).contains("## How to verify").doesNotContain("## Delivery");
        assertThat(github.world().get("blockedBy").get("9").toString()).isEqualTo("[3]");
        assertThat(github.world().get("subIssues").get("5").toString()).isEqualTo("[9]");
    }

    @Test
    void aRunQueuesTheTaskOnTheIssuesKeyAndHoldsItWithoutAPullRequest() throws IOException, InterruptedException {
        StandInGitHub.Run run = backlogItem(draft.toString());

        assertThat(run.exitCode()).as(run.all()).isZero();
        assertThat(ediCalls()).contains(
                "edi task add --kind ship --project EDI --title Harbour lamps light themselves at dusk --key EDI-9 --body -",
                "edi hold harbour-lamps --kind deferral --reason Backlogged upstream as https://github.com/example/edi/issues/9;"
                        + " it is taken from there, never from the local queue");
        assertThat(github.writes()).noneMatch(call -> call.contains("/pulls"));
        assertThat(run.output()).contains("""
                EDI-9 Harbour lamps light themselves at dusk
                  issue: https://github.com/example/edi/issues/9
                """);
    }

    @Test
    void theCaptainsApprovalIsAddedOnlyWhenTheDraftNamesHisWord() throws IOException, InterruptedException {
        backlogItem(draft.toString());
        Files.writeString(draft, DRAFT.replace("title: Harbour lamps light themselves at dusk", "title: The beacon turns all night")
                .replace("status: open", "status: open\ncaptain-approved: the captain's word of 4 November 2026"));
        Files.delete(state.resolve("task"));

        StandInGitHub.Run approved = backlogItem(draft.toString());

        assertThat(approved.exitCode()).as(approved.all()).isZero();
        assertThat(github.issue(9).get("labels").toString()).doesNotContain("captain-approved");
        assertThat(github.issue(10).get("labels").toString()).contains("captain-approved");
        assertThat(approved.output()).contains("Add captain-approved to EDI-10, on the captain's word of 4 November 2026");
    }

    @Test
    void aHeldItemCarriesTheHeldLabelAndItsReason() throws IOException, InterruptedException {
        Files.writeString(draft, DRAFT.replace("status: open", "status: held: until the captain's word"));

        StandInGitHub.Run run = backlogItem(draft.toString());

        assertThat(run.exitCode()).as(run.all()).isZero();
        assertThat(github.issue(9).get("labels").toString()).isEqualTo("[\"cloud-ok\",\"held\"]");
        assertThat(github.issue(9).get("body").asString()).endsWith("\n## Held\n\nuntil the captain's word\n");
    }

    @Test
    void aDryRunNamesEveryActionAndChangesNothing() throws IOException, InterruptedException {
        StandInGitHub.Run run = backlogItem("--dry-run", draft.toString());

        assertThat(run.exitCode()).as(run.all()).isZero();
        assertThat(run.output()).contains(
                "would open the issue \"Harbour lamps light themselves at dusk\" labelled cloud-ok in the milestone \"The lighthouse\":",
                "    ## Acceptance criteria",
                "would make EDI-<the new issue's number> a sub-issue of #5",
                "would mark EDI-<the new issue's number> blocked by #3",
                "would queue the ship \"Harbour lamps light themselves at dusk\" on EDI-<the new issue's number> with edi task add",
                "would hold the new task");
        assertThat(github.writes()).isEmpty();
        assertThat(ediCalls()).noneMatch(call -> call.startsWith("edi task add") || call.startsWith("edi hold"));
    }

    @Test
    void aRerunReusesTheIssueTheLinksAndTheHold() throws IOException, InterruptedException {
        backlogItem(draft.toString());
        Files.delete(state.resolve("calls"));
        Files.delete(state.resolve("edi-calls"));

        StandInGitHub.Run rerun = backlogItem(draft.toString());

        assertThat(rerun.exitCode()).as(rerun.all()).isZero();
        assertThat(github.writes()).isEmpty();
        assertThat(ediCalls()).noneMatch(call -> call.startsWith("edi task add") || call.startsWith("edi hold") || call.startsWith("edi release"));
        assertThat(rerun.output()).contains("issue: https://github.com/example/edi/issues/9");
    }

    @Test
    void aRerunAfterTheTitleWasNotKeyedKeysIt() throws IOException, InterruptedException {
        Files.writeString(state.resolve("github.json"), WORLD.replace("""
                   {"number": 5,""", """
                   {"number": 9, "id": 1009, "title": "Harbour lamps light themselves at dusk", "body": "", "state": "open", "milestone": 1, "labels": ["cloud-ok"]},
                   {"number": 5,"""));

        StandInGitHub.Run rerun = backlogItem(draft.toString());

        assertThat(rerun.exitCode()).as(rerun.all()).isZero();
        assertThat(github.issue(9).get("title").asString()).isEqualTo("EDI-9 Harbour lamps light themselves at dusk");
        assertThat(github.writes()).noneMatch(call -> call.contains("--method POST repos/example/edi/issues --input"));
    }

    @Test
    void aTopLevelHeadingBecomesASectionButACodeBlockKeepsItsComments() throws IOException, InterruptedException {
        Files.writeString(draft, DRAFT.replace("## Goal", "# Goal").replace("- `./mvnw verify`.", """
                ```sh
                # build both executables
                ./mvnw verify
                ```"""));

        StandInGitHub.Run run = backlogItem(draft.toString());

        assertThat(run.exitCode()).as(run.all()).isZero();
        assertThat(github.issue(9).get("body").asString()).contains("\n## Goal\n").contains("```sh\n# build both executables\n./mvnw verify\n```");
    }

    @Test
    void aDraftWithoutTheSpecsSectionsIsRefusedBeforeAnyChange() throws IOException, InterruptedException {
        Files.writeString(draft, DRAFT.replace("## Read first", "## Reading"));

        StandInGitHub.Run run = backlogItem(draft.toString());

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.errors()).contains("the spec lacks the sections \"## Read first\"");
        assertThat(github.writes()).isEmpty();
        assertThat(ediCalls()).containsExactly("edi task list --json");
    }

    @Test
    void aDraftWhoseSpecStillHoldsATodoLineIsRefusedBeforeAnyChange() throws IOException, InterruptedException {
        Files.writeString(draft, DRAFT.replace("## Read first\n", "## Read first\n\n- TODO: the code the rule changes.\n"));

        StandInGitHub.Run run = backlogItem(draft.toString());

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.errors()).contains("the spec still holds a TODO line; replace each one before the item is backlogged.");
        assertThat(github.writes()).isEmpty();
    }

    @Test
    void aDraftNamingAnUnknownMilestoneOrBlockerIsRefusedBeforeAnyChange() throws IOException, InterruptedException {
        Files.writeString(draft, DRAFT.replace("milestone: The lighthouse", "milestone: The far shore"));
        StandInGitHub.Run unknownMilestone = backlogItem(draft.toString());
        Files.writeString(draft, DRAFT.replace("blocked-by: EDI-85", "blocked-by: EDI-404"));
        StandInGitHub.Run unknownBlocker = backlogItem(draft.toString());

        assertThat(unknownMilestone.exitCode()).isEqualTo(1);
        assertThat(unknownMilestone.errors()).contains("no open milestone is titled \"The far shore\"; the open ones are \"The lighthouse\".");
        assertThat(unknownBlocker.exitCode()).isEqualTo(1);
        assertThat(unknownBlocker.errors()).contains("EDI-404 names no issue upstream.");
        assertThat(github.writes()).isEmpty();
    }

    private StandInGitHub.Run backlogItem(String... arguments) throws IOException, InterruptedException {
        return github.run(processes, directory, Map.of(
                "EDI_HOME", directory.resolve("home").toString(),
                "GIT_CONFIG_GLOBAL", "/dev/null",
                "GIT_CONFIG_NOSYSTEM", "1"), StandInGitHub.node(SCRIPT, arguments));
    }

    private List<String> ediCalls() throws IOException {
        Path calls = state.resolve("edi-calls");
        return Files.exists(calls) ? Files.readAllLines(calls) : List.of();
    }
}
