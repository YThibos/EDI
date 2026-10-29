package io.github.ythibos.edi.claude;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.InstalledPrograms;
import io.github.ythibos.edi.testing.Patience;
import io.github.ythibos.edi.testing.StandInScripts;
import io.github.ythibos.edi.testing.TestProcesses;

@Tag("adapter")
class BacklogItemSkillTest {

    private static final Path SCRIPT = Path.of(System.getProperty("edi.repository.root", "..")).toAbsolutePath().normalize()
            .resolve("claude/skills/edi-backlog-item/backlog-item.mjs");
    private static final String UPSTREAM = "https://github.com/example/edi";
    private static final String BRANCH = "chore/EDI-143";
    private static final String ITEM = "docs/backlog/EDI-143.md";
    private static final String INDEX = "docs/backlog/README.md";
    private static final String THE_INDEX = """
            # The backlog

            ## The index

            ### Wave 1: no wait

            | Order | Item | Title | Size | Blocked by | Status | Issue |
            |---|---|---|---|---|---|---|
            | 1 | [EDI-85](EDI-85.md) | The stage is stored | M | - | open | - |

            ### Held, parked and needs the captain's machine, no wave

            | Order | Item | Title | Size | Blocked by | Status | Issue |
            |---|---|---|---|---|---|---|
            | - | [EDI-110](EDI-110.md) | The header hides its slots | XS | - | held: until the captain's word. | - |

            ## Not in the backlog
            """;
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
            section: Wave 1
            blocked-by: EDI-85
            status: open
            ---
            """ + SPEC;
    private static final String STAND_IN_GH = """
            #!/bin/sh
            echo "gh $*" >> "$STATE/calls"
            case "$1 $2" in
              "pr list") if [ -f "$STATE/pull" ]; then printf '[{"url":"%s"}]' "$(cat "$STATE/pull")"; else echo '[]'; fi ;;
              "pr create") cat > "$STATE/pull-body"; echo https://github.com/example/edi/pull/7 | tee "$STATE/pull" ;;
              "issue list") if [ -f "$STATE/issue" ]; then printf '[{"number":9,"title":"%s","url":"https://github.com/example/edi/issues/9"}]' "$(cat "$STATE/issue")"; else echo '[]'; fi ;;
              "issue create") cat > "$STATE/issue-body"; printf '%s' "$4" > "$STATE/issue"; echo https://github.com/example/edi/issues/9 ;;
              "label list") echo '[{"name":"cloud-ok"}]' ;;
            esac
            """;
    private static final String STAND_IN_EDI = """
            #!/bin/sh
            echo "edi $*" >> "$STATE/calls"
            case "$1 $2" in
              "task list") if [ -f "$STATE/task" ]; then echo '{"tasks":[{"id":"harbour-lamps","intake":{"title":"Harbour lamps light themselves at dusk","projects":["EDI"]}}]}'; else echo '{"tasks":[]}'; fi ;;
              "task add") cat > "$STATE/task"; echo '{"added":{"id":"harbour-lamps"}}' ;;
              "task show") hold=null; if [ -f "$STATE/hold" ]; then hold=$(printf '{"kind":"deferral","reason":"%s","until":null}' "$(cat "$STATE/hold")"); fi
                printf '{"task":{"id":"harbour-lamps","intake":{"kind":"ship","title":"Harbour lamps light themselves at dusk","projects":["EDI"],"workKey":"EDI-143"},"notes":"","hold":%s}}' "$hold" ;;
            esac
            if [ "$1" = hold ]; then printf '%s' "$6" > "$STATE/hold"; fi
            """;

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    private FixtureRepository upstream;
    private Path state;
    private Path draft;

    @BeforeEach
    void anUpstreamWithTheIndexAndAnEdiHomeCloningIt() throws IOException {
        assumeTrue(InstalledPrograms.answers("node", "--version"), "node is not installed");
        FixtureRepository seed = FixtureRepository.initialise(Files.createDirectories(directory.resolve("seed"))).write(INDEX, THE_INDEX).commit("EDI-117 Add the backlog");
        seed.run("clone", "--quiet", "--bare", ".", directory.resolve("upstream.git").toString());
        upstream = FixtureRepository.at(directory.resolve("upstream.git"));
        upstream.clonedInto(directory.resolve("home/projects/EDI"))
                .configure("remote.origin.url", UPSTREAM)
                .configure("url." + directory.resolve("upstream.git") + ".insteadOf", UPSTREAM);
        state = Files.createDirectories(directory.resolve("state"));
        StandInScripts.installed(directory.resolve("bin/gh"), STAND_IN_GH);
        StandInScripts.installed(directory.resolve("bin/edi"), STAND_IN_EDI);
        draft = Files.writeString(directory.resolve("harbour-lamps.md"), DRAFT);
    }

    @Test
    void aRunAddsTheItemAndItsRowOnABranchWithoutTrailers() throws IOException, InterruptedException {
        Run run = backlogItem(draft.toString());

        assertThat(run.exitCode()).as(run.output()).isZero();
        assertThat(upstream.run("show", BRANCH + ":" + ITEM)).startsWith("""
                # EDI-143 Harbour lamps light themselves at dusk

                | Field | Value |
                |---|---|
                | Key | EDI-143 |
                | Kind | ship, a change to the repository, on branch `feature/EDI-143` |
                | Wave and order | wave 1, order 2 |
                | Source | The captain's word of 29 October 2026 |
                | Size | S |
                | Blocked by | [EDI-85](EDI-85.md) |
                | Status | open |
                | Cloud session | ok |

                ## Goal
                """).contains("## How to verify").contains("## Delivery");
        assertThat(upstream.run("diff", "main", BRANCH, "--", INDEX)).containsOnlyOnce("\n+|").contains("""
                +| 2 | [EDI-143](EDI-143.md) | Harbour lamps light themselves at dusk | S | EDI-85 | open | [#9](https://github.com/example/edi/issues/9) |""");
        assertThat(upstream.run("log", "--format=%B", "main.." + BRANCH).strip()).isEqualTo("""
                EDI-143 Name the issue of the backlog item

                EDI-143 Add the backlog item: Harbour lamps light themselves at dusk""");
    }

    @Test
    void aRunOpensThePullRequestAndTheIssueAndHoldsTheLocalTask() throws IOException, InterruptedException {
        Run run = backlogItem(draft.toString());

        assertThat(run.exitCode()).as(run.output()).isZero();
        assertThat(calls()).contains(
                "edi task add --json --kind ship --project EDI --title Harbour lamps light themselves at dusk --body -",
                "gh label create wave-1 --description Backlog wave 1 --repo github.com/example/edi",
                "gh issue create --title EDI-143 Harbour lamps light themselves at dusk --label wave-1,cloud-ok --body-file - --repo github.com/example/edi",
                "gh pr create --base main --head chore/EDI-143 --title EDI-143 Backlog: Harbour lamps light themselves at dusk --body-file - --repo github.com/example/edi",
                "edi hold harbour-lamps --kind deferral --reason Backlogged upstream as https://github.com/example/edi/issues/9,"
                        + " added by https://github.com/example/edi/pull/7; it is taken from there, never from the local queue");
        assertThat(Files.readString(state.resolve("issue-body")))
                .isEqualTo("The spec is [docs/backlog/EDI-143.md](https://github.com/example/edi/blob/main/docs/backlog/EDI-143.md).\nBlocked by EDI-85.\n");
        assertThat(run.output()).contains("""
                EDI-143
                  doc:   docs/backlog/EDI-143.md
                  pull:  https://github.com/example/edi/pull/7
                  issue: https://github.com/example/edi/issues/9
                """);
        assertThat(FixtureRepository.at(directory.resolve("home/projects/EDI")).run("worktree", "list").strip().lines()).hasSize(1);
    }

    @Test
    void aDryRunNamesEveryActionAndChangesNothing() throws IOException, InterruptedException {
        Run run = backlogItem("--dry-run", draft.toString());

        assertThat(run.exitCode()).as(run.output()).isZero();
        assertThat(run.output()).contains(
                "would queue the ship \"Harbour lamps light themselves at dusk\" with edi task add",
                "would write docs/backlog/EDI-<new 1>.md",
                "would commit \"EDI-<new 1> Add the backlog item: Harbour lamps light themselves at dusk\"",
                "would open the issue \"EDI-<new 1> Harbour lamps light themselves at dusk\" labelled wave-1, cloud-ok",
                "would push chore/EDI-<new 1>",
                "would open the pull request",
                "would hold the new task");
        assertThat(upstream.run("branch", "--list").strip()).isEqualTo("* main");
        assertThat(calls()).noneMatch(call -> call.contains(" create ") || call.startsWith("edi task add") || call.startsWith("edi hold"));
    }

    @Test
    void aRerunReusesTheItemThePullRequestAndTheIssue() throws IOException, InterruptedException {
        backlogItem(draft.toString());
        String delivered = upstream.run("rev-parse", BRANCH);
        Files.delete(state.resolve("calls"));

        Run rerun = backlogItem(draft.toString());

        assertThat(rerun.exitCode()).as(rerun.output()).isZero();
        assertThat(upstream.run("rev-parse", BRANCH)).isEqualTo(delivered);
        assertThat(calls()).noneMatch(call -> call.contains(" create ") || call.startsWith("edi task add") || call.startsWith("edi hold")
                || call.startsWith("edi release"));
        assertThat(rerun.output()).contains("pull:  https://github.com/example/edi/pull/7", "issue: https://github.com/example/edi/issues/9");
    }

    @Test
    void aDraftWithoutTheSpecsSectionsIsRefusedBeforeAnyChange() throws IOException, InterruptedException {
        Files.writeString(draft, DRAFT.replace("## Read first", "## Reading"));

        Run run = backlogItem(draft.toString());

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.output()).contains("the spec lacks the sections \"## Read first\"");
        assertThat(calls()).containsExactly("edi task list --json");
    }

    private Run backlogItem(String... arguments) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command(arguments)).directory(directory.toFile()).redirectErrorStream(true);
        builder.environment().keySet().removeIf(variable -> variable.startsWith("GIT_CONFIG_") || variable.startsWith("GH_"));
        builder.environment().putAll(isolatedFrom(builder.environment().get("PATH")));
        Process process = processes.register(builder.start());
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(Patience.FOR_REAL_WORK.toSeconds(), TimeUnit.SECONDS)).as("the script finished").isTrue();
        return new Run(process.exitValue(), output);
    }

    private static List<String> command(String... arguments) {
        List<String> command = new ArrayList<>(List.of("node", SCRIPT.toString()));
        command.addAll(List.of(arguments));
        return command;
    }

    private Map<String, String> isolatedFrom(String path) {
        return Map.of(
                "PATH", directory.resolve("bin") + ":" + path,
                "EDI_HOME", directory.resolve("home").toString(),
                "STATE", state.toString(),
                "GIT_CONFIG_GLOBAL", "/dev/null",
                "GIT_CONFIG_NOSYSTEM", "1",
                "GIT_AUTHOR_NAME", "Fixture Author",
                "GIT_AUTHOR_EMAIL", "fixture@example.org",
                "GIT_COMMITTER_NAME", "Fixture Author",
                "GIT_COMMITTER_EMAIL", "fixture@example.org");
    }

    private List<String> calls() throws IOException {
        Path calls = state.resolve("calls");
        return Files.exists(calls) ? Files.readAllLines(calls) : List.of();
    }

    private record Run(int exitCode, String output) {
    }
}
