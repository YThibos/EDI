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
    void carriesTheLearningsWholeAndKeepsAPreambleOnlyWhenItHoldsEntries() throws IOException {
        write("learnings.md", "# Learnings\n\nCurated facts.\n\n- An entry before any section. (2026-10-01)\n\n## Shop\n\n- The lamp needs oil. (2026-10-01)\n");

        String learnings = contentOf(MemoryImport.of(ForkHome.at(home), Set.of()).files(ForkHome.at(home)), MemoryImport.LEARNINGS);

        assertThat(learnings).startsWith("# Learnings\n\nOperational facts, each dated")
                .contains("Curated facts.\n\n- An entry before any section. (2026-10-01)\n\n## Shop\n\n- The lamp needs oil. (2026-10-01)\n")
                .doesNotContain("# Learnings\n\nCurated");
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
        return files.stream().filter(file -> file.path().equals(path)).findFirst().orElseThrow().content();
    }
}
