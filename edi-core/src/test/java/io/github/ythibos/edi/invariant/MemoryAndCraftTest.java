package io.github.ythibos.edi.invariant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.web.report.ReportWidth;
import io.github.ythibos.edi.web.report.Turn;

@DisplayName("4.7 Memory and craft")
class MemoryAndCraftTest {

    @TempDir
    private Path directory;

    @Nested
    @DisplayName("I-33 Memory is curated, not appended, and fits a budget")
    class MemoryFitsABudget {

        @Test
        @DisplayName("AT-33 edi memory check fails above the budget and on a duplicated rule heading")
        void theMemoryCheckEnforcesTheBudget() {
            Pending.until(Slice.S8);
        }
    }

    @Nested
    @DisplayName("I-34 Craft rules travel with the work")
    class CraftRulesTravelWithTheWork {

        @Test
        @DisplayName("AT-34 the pipeline intent for a craftRules project holds the rules block verbatim and the acceptance criteria")
        void theIntentCarriesTheCraftRules() {
            Pending.until(Slice.S5);
        }
    }

    @Nested
    @DisplayName("I-35 The always-loaded anchor stays small")
    class TheAnchorStaysSmall {

        private static final long ANCHOR_BUDGET_BYTES = 10_000;

        @Test
        @DisplayName("AT-35 the primary anchor, with its personal addendum, is at most 10,000 bytes")
        void thePrimaryAnchorStaysWithinItsBudget() throws IOException {
            Path anchor = RepositoryRoot.path().resolve("claude/primary/CLAUDE.md");
            assumeTrue(Files.exists(anchor), "pending until slice " + Slice.S8 + ": the primary anchor does not exist yet");

            long loaded = Files.size(anchor) + sizeWhenPresent(RepositoryRoot.path().resolve("personal/anchor.local.md"));

            assertThat(loaded).as("bytes of the primary anchor and its personal addendum").isLessThanOrEqualTo(ANCHOR_BUDGET_BYTES);
        }

        @Test
        @DisplayName("AT-35 the repository's CLAUDE.md, which every agent working on EDI loads, is at most 10,000 bytes")
        void theRepositoryAnchorStaysWithinItsBudget() throws IOException {
            Path anchor = RepositoryRoot.path().resolve("CLAUDE.md");

            assertThat(Files.size(anchor)).as("bytes of the repository's CLAUDE.md").isLessThanOrEqualTo(ANCHOR_BUDGET_BYTES);
        }

        @Test
        @DisplayName("AT-35 a generated worker brief is at most 120 lines before the task's own intent and spec")
        void aGeneratedBriefStaysWithinItsBudget() {
            Pending.until(Slice.S3);
        }

        private static long sizeWhenPresent(Path file) throws IOException {
            return Files.exists(file) ? Files.size(file) : 0;
        }
    }

    @Nested
    @DisplayName("I-36 Fleet-sized data never travels as a command-line argument")
    class FleetSizedDataNeverTravelsAsAnArgument {

        private static final long TWO_MEGABYTES = 2_000_000;
        private static final int SITREP_PANE_COLUMNS = 90;

        @Test
        @Tag("adapter")
        @DisplayName("AT-36 the captain report renders from a 2 MB backlog fixture")
        void theCaptainReportRendersFromALargeBacklog() throws IOException {
            TestHome home = ReportGoldenCases.homeIn(directory);
            ReportGoldenCases.queuedBacklog(home, ReportGoldenCases.TWO_MEGABYTE_TASKS, ReportGoldenCases.TWO_MEGABYTE_NOTE_WORDS);

            String report = ReportGoldenCases.rendered(home, Turn.NONE, ReportWidth.standard());

            assertThat(charactersOfTheBacklog(home)).isGreaterThanOrEqualTo(TWO_MEGABYTES);
            assertThat(report).contains("BACKLOG  (512)", "Queued work item 1", "(+507 more queued)");
        }

        @Test
        @Tag("adapter")
        @DisplayName("AT-36 the text sitrep renders from a 2 MB backlog fixture")
        void theTextSitrepRendersFromALargeBacklog() throws IOException {
            TestHome home = ReportGoldenCases.homeIn(directory);
            ReportGoldenCases.queuedBacklog(home, ReportGoldenCases.TWO_MEGABYTE_TASKS, ReportGoldenCases.TWO_MEGABYTE_NOTE_WORDS);

            String sitrep = ReportGoldenCases.rendered(home, Turn.NONE, ReportWidth.forPane(SITREP_PANE_COLUMNS));

            assertThat(charactersOfTheBacklog(home)).isGreaterThanOrEqualTo(TWO_MEGABYTES);
            assertThat(sitrep.lines().findFirst().orElseThrow()).startsWith("╔══ NEEDS YOUR DECISION ══ ").hasSize(SITREP_PANE_COLUMNS - 2);
            assertThat(sitrep).contains("DONE THIS TURN  (0)", "BACKLOG  (512)", "Queued work item 1", "(+507 more queued)");
        }

        @Test
        @DisplayName("AT-36 the sitrep board renders from a 2 MB backlog fixture")
        void theSitrepBoardRendersFromALargeBacklog() {
            Pending.until(Slice.S7);
        }

        private static long charactersOfTheBacklog(TestHome home) {
            return home.jdbc().sql("select sum(length(title) + length(notes)) from task").query(Long.class).single();
        }
    }
}
