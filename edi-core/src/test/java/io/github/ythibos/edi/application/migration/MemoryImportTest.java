package io.github.ythibos.edi.application.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.fork.ForkHome;

@Tag("adapter")
class MemoryImportTest {

    @TempDir
    private Path home;

    @Test
    void routesEachSectionByWhatItsHeadingConcerns() throws IOException {
        write("captain.md", """
                # Captain preferences

                Live policy for this home.

                ## Identity and quality bar

                - Quality first. (2026-07-01)

                ## JIRA and timesheets

                - Book every block of work. (2026-10-07)

                ## Decision log in harbour (HARD RULE)

                - Each decision gets a record. (2026-10-13)

                ## Harbourmaster duties

                - Not about the harbour project. (2026-10-14)

                ## firstofficer charts its own course

                - The old harness improves itself. (2026-07-29)

                ## E.D.I. - the rewrite of firstofficer

                - EDI work runs on the strongest model. (2026-10-15)
                """);
        write("captain-shared.md", "# Shared\n\n## Reasoning effort\n\n- High is the ceiling. (2026-09-01)\n");

        MemoryImport memory = MemoryImport.of(ForkHome.at(home), Set.of("harbour", "lighthouse"));

        assertThat(memory.routes()).extracting(MemoryRoute::heading, MemoryRoute::file).containsExactly(
                tuple("Identity and quality bar", MemoryImport.PROFILE),
                tuple("JIRA and timesheets", MemoryImport.MACHINE),
                tuple("Decision log in harbour (HARD RULE)", MemoryImport.MACHINE),
                tuple("Harbourmaster duties", MemoryImport.PROFILE),
                tuple("firstofficer charts its own course", MemoryImport.ARCHIVE),
                tuple("E.D.I. - the rewrite of firstofficer", MemoryImport.PROFILE),
                tuple("Reasoning effort", MemoryImport.PROFILE));
        assertThat(contentOf(memory.files(ForkHome.at(home)), MemoryImport.PROFILE)).isEqualTo("""
                # Profile

                The captain's rules on every machine.

                ## Identity and quality bar

                - Quality first. (2026-07-01)

                ## Harbourmaster duties

                - Not about the harbour project. (2026-10-14)

                ## E.D.I. - the rewrite of firstofficer

                - EDI work runs on the strongest model. (2026-10-15)

                ## Reasoning effort

                - High is the ceiling. (2026-09-01)
                """);
    }

    @Test
    void keepsAPreambleOnlyWhenItHoldsEntriesAndDropsTheOldHarnesssMechanicsFromTheLearnings() throws IOException {
        write("learnings.md", """
                # Learnings

                Curated facts.

                - An entry before any section. (2026-10-01)

                ## Shop

                - The lamp needs oil. (2026-10-01)

                - **The command guard refuses commands naming a watcher script (2026-09-14).**
                  The guard matches the script's name.

                - **A dead worker is reclaimed by `fm-control.sh <id> relaunch` (2026-10-14).**
                  It restarts in the same copy.

                - **Docker can drop its WSL integration (2026-10-14).**
                  Re-enable it in the settings.
                """);

        HomeFile learnings = fileOf(MemoryImport.of(ForkHome.at(home), Set.of()).files(ForkHome.at(home)), MemoryImport.LEARNINGS);

        assertThat(learnings.content()).isEqualTo("""
                # Learnings

                Operational facts, each dated with the day it was last confirmed, with the evidence and the rule it produced.

                Curated facts.

                - An entry before any section. (2026-10-01)

                ## Shop

                - The lamp needs oil. (2026-10-01)

                - **Docker can drop its WSL integration (2026-10-14).**
                  Re-enable it in the settings.
                """);
        assertThat(learnings.holds()).isEqualTo("1 section without 2 old-harness learnings");
    }

    @Test
    void generalisesTheLearningsKeptOnlyInPart() throws IOException {
        write("learnings.md", """
                # Learnings

                ## Shop

                - **`gh-axi` resolves pull requests against UPSTREAM harbour, not this fork.**
                  Always pass `--repo someone/harbour` when reading this repo's pull requests. <!--a:2026-10-01-->

                - **Never `cd` at the top level in this home; use a subshell (2026-10-07).**
                  A guard denies a persistent `cd`.
                  `cmd.exe` refuses a WSL working directory.
                """);

        String learnings = contentOf(MemoryImport.of(ForkHome.at(home), Set.of()).files(ForkHome.at(home)), MemoryImport.LEARNINGS);

        assertThat(learnings).endsWith("""
                ## Shop

                - **`gh-axi` can resolve pull requests against an upstream repository instead of the fork it runs in (2026-10-01).**
                  Always pass `--repo <owner>/<name>` when reading a repository's pull requests.

                - **`cmd.exe` refuses a WSL working directory (2026-10-07).**
                  Launch a Windows tool from a subshell that first changes to a directory on a Windows drive.
                """);
    }

    @Test
    void editsTheCaptainsRulesOnTheWay() throws IOException {
        write("captain.md", """
                # Captain preferences

                ## Global personal preferences - POINTER, do not restate

                His rules live in `/home/someone/AGENTS.md`.

                ## Hard rules on every project commit

                - **Never use an `fm/` branch name.**
                  Every branch is JIRA-keyed: `feature/<KEY>-N`.
                  It slipped again: `fm-project-mode.sh --branch-prefix` answers `fm/`.

                ## Delivery flow (current)

                1. **Work and commit** on the task's branch.

                Supporting rules:

                - **Base branch per project:** harbour branches from `origin/master`.
                  Branch from the origin ref.
                - **The independent craftsmanship review is removed from ALL flows (decision 2026-09-24).**
                  The review list now names no project.
                - **Keep the task copy until the work has landed**, then clean up.

                ## Applying changes locally, without committing

                Apply the edits into his checkout at `/home/someone/sources/<repo>` and do not commit.

                ## Reporting format to the captain

                The report is rendered by `bin/fm-captain-report.sh` and pasted verbatim.
                """);

        String profile = contentOf(MemoryImport.of(ForkHome.at(home), Set.of()).files(ForkHome.at(home)), MemoryImport.PROFILE);

        assertThat(profile).isEqualTo("""
                # Profile

                The captain's rules on every machine.

                ## Global personal preferences - POINTER, do not restate

                The captain's project-independent style rules live in one authoritative file, the one the machine profile names as `captain.globalRules`.
                Read it when a style question arises; do not copy its rules here.

                ## Hard rules on every project commit

                - **Never use an `fm/` branch name.**
                  Every branch is keyed with the task's work key (JIRA on the laptop, EDI-native elsewhere): `feature/<KEY>-N`.

                ## Delivery flow (current)

                1. **Work and commit** on the task's branch.

                Supporting rules:

                - **The independent craftsmanship reviewer is retired from all flows (2026-09-24); the craft rules ride in the validation pipeline instead.**
                - **Keep the task copy until the work has landed**, then clean up.

                ## Applying changes locally, without committing

                Apply the edits into his checkout at `<captain.checkoutsRoot>/<repo>` (the checkout root from the machine profile) and do not commit.

                ## Reporting format to the captain

                The report is rendered by `edi report` and pasted verbatim.
                """);
    }

    @Test
    void writesEveryStartupFileEvenWhenTheOldHomeHasNoMemory() throws IOException {
        write("backlog.md", "");

        List<HomeFile> files = MemoryImport.of(ForkHome.at(home), Set.of()).files(ForkHome.at(home));

        assertThat(files).extracting(HomeFile::path)
                .containsExactly(MemoryImport.PROFILE, MemoryImport.MACHINE, MemoryImport.LEARNINGS, MemoryImport.ARCHIVE);
        assertThat(contentOf(files, MemoryImport.ARCHIVE)).isEqualTo("# Archive\n\nRetired memory, kept for reference and never loaded at session start.\n");
    }

    private void write(String file, String content) throws IOException {
        Files.createDirectories(home.resolve("data"));
        if (!Files.exists(home.resolve("data/backlog.md"))) {
            Files.writeString(home.resolve("data/backlog.md"), "");
        }
        Files.writeString(home.resolve("data").resolve(file), content);
    }

    private static String contentOf(List<HomeFile> files, String path) {
        return fileOf(files, path).content();
    }

    private static HomeFile fileOf(List<HomeFile> files, String path) {
        return files.stream().filter(file -> file.path().equals(path)).findFirst().orElseThrow();
    }
}
