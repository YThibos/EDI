package io.github.ythibos.edi.adapter.fork;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.OptionalInt;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("adapter")
class ForkHomeTest {

    @TempDir
    private Path home;

    @Test
    void refusesAHomeWithoutABacklog() {
        assertThatIllegalArgumentException().isThrownBy(() -> ForkHome.at(home)).withMessageContaining("has no data/backlog.md");
    }

    @Test
    void findsTheHighestDecisionNumberInAnyOfItsFiles() throws IOException {
        write("backlog.md", "Captain decision id: D12\n");
        write("decision-seq", "40\n");
        write("done-archive.md", "D61 settled it; D2x, xD99 and D1234567890 are not decision numbers.\n");
        write("note-archive.md", "see D45.\n");

        SeenNumber highest = ForkHome.at(home).highestDecisionNumber();

        assertThat(highest).isEqualTo(new SeenNumber(61, "data/done-archive.md"));
    }

    @Test
    void prefersTheRecordedSequenceOnATie() throws IOException {
        write("backlog.md", "D57\n");
        write("decision-seq", "57");

        assertThat(ForkHome.at(home).highestDecisionNumber()).isEqualTo(new SeenNumber(57, "data/decision-seq"));
    }

    @Test
    void startsFromNothingWhenNothingIsNumbered() throws IOException {
        write("backlog.md", "# Backlog\n");

        assertThat(ForkHome.at(home).highestDecisionNumber().value()).isZero();
    }

    @Test
    void findsTheHighestWorkKeyOfAProjectInTitlesBodiesAndArchives() throws IOException {
        write("backlog.md", """
                ## Queued
                - [ ] a - EDI-4 one
                  Its branch is feature/EDI-14, named in its card.
                - [ ] b - fix (EDI-12) and EDI-9
                - [ ] c - NOTEDI-99 and EDI-x
                """);
        write("done-archive.md", "- [x] old - EDI-13 done long ago\n");

        assertThat(ForkHome.at(home).highestWorkKeyNumber("EDI")).isEqualTo(new SeenNumber(14, "data/backlog.md"));
        assertThat(ForkHome.at(home).highestWorkKeyNumber("HRN").value()).isZero();
    }

    @Test
    void readsEachRegisteredProjectWithItsBaseBranch() throws IOException {
        write("backlog.md", "");
        write("projects.md", """
                # Registry

                - harbour [local-only] - base origin/master; the harbour app on an internal forge (added 2026-07-07)
                - ledger [local-only] - captain-owned copy; base main; a database tool (added 2026-07-10)
                - drafts [no-mistakes] - no base named here (added 2026-07-30)
                Local-only flow: not a project line.
                - two words [local-only] - base origin/main
                """);

        assertThat(ForkHome.at(home).projects()).containsExactly(
                new ForkProject("harbour", "master"), new ForkProject("ledger", "main"), new ForkProject("drafts", null));
    }

    @Test
    void readsTheRulesTheCraftScriptPrintsAndTheProjectsThatCarryThem() throws IOException {
        write("backlog.md", "");
        Files.createDirectories(home.resolve("bin"));
        Files.writeString(home.resolve("bin/fm-craft-rules.sh"), """
                #!/usr/bin/env bash
                usage() {
                  cat <<'EOF'
                not the rules
                EOF
                }
                print_rules() {
                  cat <<'EOF'
                Craftsmanship rules this change must meet:
                - Short methods.
                EOF
                }
                """);
        Files.createDirectories(home.resolve("config"));
        Files.writeString(home.resolve("config/craft-rules-projects"), "# who carries them\nharbour\n  ledger # since July\n\n");

        assertThat(ForkHome.at(home).craftRules()).hasValue("Craftsmanship rules this change must meet:\n- Short methods.\n");
        assertThat(ForkHome.at(home).craftRulesProjects()).containsExactly("harbour", "ledger");
    }

    @Test
    void readsTheSettingsTheOldHomeKeepsAsFiles() throws IOException {
        write("backlog.md", "");
        Files.createDirectories(home.resolve("config"));

        assertThat(ForkHome.at(home).keepsAFleetLedger()).isFalse();
        assertThat(ForkHome.at(home).startupMemoryBudget()).isEmpty();

        Files.writeString(home.resolve("config/fleet-ledger"), "");
        Files.writeString(home.resolve("config/startup-memory-budget"), "18000\n");

        assertThat(ForkHome.at(home).keepsAFleetLedger()).isTrue();
        assertThat(ForkHome.at(home).startupMemoryBudget()).isEqualTo(OptionalInt.of(18_000));
    }

    @Test
    void archivesTheDataFilesTaskFoldersConfigurationAndHeldWork() throws IOException {
        write("backlog.md", "# Backlog\n");
        write("lamp/report.md", "The lamp report.\n");
        write("lamp/screens/one.png", "png");
        Files.createSymbolicLink(home.resolve("data/lamp/latest"), Path.of("report.md"));
        Files.createDirectories(home.resolve("config"));
        Files.writeString(home.resolve("config/crew-dispatch.json"), "{}");
        Files.createDirectories(home.resolve("state/held"));
        Files.writeString(home.resolve("state/held/lamp.md"), "held");
        Files.writeString(home.resolve("state/lamp.status"), "working");

        assertThat(ForkHome.archiveOf(home)).extracting(ArchivedFile::path).containsExactly(
                "backlog.md", "config/crew-dispatch.json", "state/held/lamp.md", "tasks/lamp/latest", "tasks/lamp/report.md",
                "tasks/lamp/screens/one.png");
    }

    @Test
    void readsThePauseResurfaceTheOldHarnessWasStartedWith() {
        assertThat(new ForkEnvironment("14400").pauseResurface()).hasValue(Duration.ofHours(4));
        assertThat(new ForkEnvironment("").pauseResurface()).isEmpty();
        assertThat(new ForkEnvironment("soon").pauseResurface()).isEmpty();
    }

    private void write(String file, String content) throws IOException {
        Path written = home.resolve("data").resolve(file);
        Files.createDirectories(written.getParent());
        Files.writeString(written, content);
    }
}
