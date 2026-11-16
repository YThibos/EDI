package io.github.ythibos.edi.invariant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.brief.WorkerBriefs;
import io.github.ythibos.edi.application.dispatch.DispatchRequest;
import io.github.ythibos.edi.application.memory.MemoryCheck;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.status.CaptainAddress;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.testing.BriefFixtures;
import io.github.ythibos.edi.testing.ManualClock;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;
import io.github.ythibos.edi.web.PresenterFixtures;
import io.github.ythibos.edi.web.console.ConsoleSitrep;
import io.github.ythibos.edi.web.memory.MemoryCheckText;
import io.github.ythibos.edi.web.report.ReportWidth;
import io.github.ythibos.edi.domain.report.Turn;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("4.7 Memory and craft")
class MemoryAndCraftTest {

    @TempDir
    private Path directory;

    @Nested
    @DisplayName("I-33 Memory is curated, not appended, and fits a budget")
    class MemoryFitsABudget {

        @Test
        @DisplayName("AT-33 edi memory check fails above the budget and on a duplicated rule heading")
        void theMemoryCheckEnforcesTheBudget() throws IOException {
            String profile = "# Profile\n\n## Commits\n\n- Every commit carries its key. (2026-10-15)\n";
            String machine = "# Machine\n\n## Tracker\n\n- Tickets live in the tracker. (2026-10-15)\n";
            TestHome home = TestHome.in(directory).writing("config/machine.yaml", "memory:\n  startupBudgetTokens: 100\n")
                    .writing("memory/profile.md", profile)
                    .writing("memory/machine.md", machine)
                    .writing("memory/archive.md", "# Archive\n\n" + "Retired words. ".repeat(500));

            MemoryCheck fitting = home.memory().check();
            home.writing("memory/learnings.md", "# Learnings\n\n- " + "A long-winded learning. ".repeat(20) + "(2026-10-15)\n");
            MemoryCheck overBudget = home.memory().check();
            home.writing("memory/learnings.md", "# Learnings\n\n## Commits\n\n- A rule written twice. (2026-10-15)\n");
            MemoryCheck duplicated = home.memory().check();

            assertThat(fitting.passes()).isTrue();
            assertThat(fitting.estimatedTokens()).isEqualTo(Math.ceilDiv(profile.length(), 4) + Math.ceilDiv(machine.length(), 4));
            assertThat(fitting.files()).extracting(MemoryCheck.FileEstimate::name).containsExactly("profile.md", "machine.md", "learnings.md");
            assertThat(overBudget.passes()).isFalse();
            assertThat(overBudget.estimatedTokens()).isGreaterThan(overBudget.budgetTokens());
            assertThat(overBudget.duplicatedHeadings()).isEmpty();
            assertThat(duplicated.passes()).isFalse();
            assertThat(duplicated.fitsItsBudget()).isTrue();
            assertThat(duplicated.duplicatedHeadings()).containsExactly("commits");
            assertThat(new MemoryCheckText().text(duplicated)).contains("The rule heading \"commits\" appears more than once", "The memory check fails");
        }
    }

    @Nested
    @DisplayName("I-34 Craft rules travel with the work")
    class CraftRulesTravelWithTheWork {

        @Test
        @DisplayName("AT-34 the pipeline intent for a craftRules project holds the rules block verbatim, the acceptance criteria and the spec")
        @Tag("adapter")
        void theIntentCarriesTheCraftRules() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            String intent = """
                    Fit the brass lamp in the hall.
                    Acceptance: AT-99 passes and the lamp lights; a draft pull request the captain merges.
                    Offline verification is accepted for anything that needs no network.""";
            String spec = "The hall switch also dims the lamp, with a test for the dimmer.";
            home.registering("""
                    projects:
                      - name: harness
                        tracker: { kind: edi, projectKey: HRN }
                        forge: { kind: github, path: example-owner/harness, account: example-owner }
                        base: main
                        craftRules: true
                      - name: EDI
                        tracker: { kind: edi, projectKey: EDI }
                        forge: { kind: github, path: example-owner/EDI, account: example-owner }
                        base: main
                    """);
            home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3").noted(intent),
                    WorkerFixtures.queuedShip("map-caves", "EDI", "EDI-9").noted(intent)));

            dispatchShip(home, "fit-lamp", spec);
            dispatchShip(home, "map-caves", spec);

            Path flagged = home.home().taskDirectory(new TaskId("fit-lamp")).resolve("intent.md");
            assertThat(Files.readString(flagged)).isEqualTo("## Captain's intent\n\nDo the work on fit-lamp.\n\n" + intent + "\n\n## Spec\n\n" + spec
                    + "\n\n" + keyedCommits("HRN-3") + "\n\n" + WorkerFixtures.CRAFT_RULES.strip());
            assertThat(Files.readString(flagged)).contains("Acceptance: AT-99 passes", "Offline verification is accepted", WorkerFixtures.CRAFT_RULES.strip());
            assertThat(Files.readString(home.home().taskDirectory(new TaskId("fit-lamp")).resolve("brief.md")))
                    .contains("no-mistakes axi run --base-branch main --skip rebase --intent \"$(cat '" + flagged + "')\"");
            assertThat(Files.readString(home.home().taskDirectory(new TaskId("map-caves")).resolve("intent.md")))
                    .isEqualTo("## Captain's intent\n\nDo the work on map-caves.\n\n" + intent + "\n\n## Spec\n\n" + spec + "\n\n" + keyedCommits("EDI-9"));
        }

        private static void dispatchShip(TestHome home, String task, String spec) {
            home.dispatchTask().dispatch(TaskReference.parse(task), new DispatchRequest(CategoryId.IMPLEMENTATION, spec, List.of(), null),
                    CallingSession.UNNAMED, Actor.PRIMARY);
        }

        private static String keyedCommits(String key) {
            return "## Commits\n\nThe pipeline starts every commit subject it writes, and the pull request's title, with the work key `" + key + "`.\n"
                    + "A fix summary or a title it asks for is one short line that never starts with a work key: not `" + key
                    + "`, which the pipeline would then write twice, and no other key either.";
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
            WorkerBriefs briefs = new WorkerBriefs();
            String longIntent = "The captain's words, one per line.\n".repeat(400);
            String longSpec = "Build instruction.\n".repeat(300);

            String brief = briefs.briefFor(BriefFixtures.largestShip(longIntent, longSpec));

            assertThat(linesOutsideTheTasksOwnSections(brief)).as("lines of the brief before the task's own intent and spec").isLessThanOrEqualTo(120);
            assertThat(brief).contains(longIntent.strip(), longSpec.strip());
            assertThat(BriefFixtures.everyKindOfBrief().map(briefs::briefFor).map(TheAnchorStaysSmall::linesOutsideTheTasksOwnSections))
                    .allSatisfy(lines -> assertThat(lines).isLessThanOrEqualTo(120));
        }

        @Test
        @Tag("adapter")
        @DisplayName("AT-35 a dispatch whose brief would run past 120 lines is refused, naming its longest section, before anything is saved or copied")
        void aDispatchRefusesABriefPastItsBudget() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));
            int specLinesWithinTheBudget = 120 - previewedBrief(home, buildInstructions(1)).lines().toList().size() + 1;

            assertThatThrownBy(() -> dispatch(home, buildInstructions(specLinesWithinTheBudget + 1)))
                    .isInstanceOf(Refusal.class).hasMessageContaining("121 lines").hasMessageContaining("its longest section is Spec");
            assertThat(home.home().worktreeOf(new ProjectName("harness"), new TaskId("fit-lamp"))).doesNotExist();
            assertThat(home.dispatches().of(new TaskId("fit-lamp"))).isEmpty();

            dispatch(home, buildInstructions(specLinesWithinTheBudget));

            assertThat(Files.readAllLines(home.home().taskDirectory(new TaskId("fit-lamp")).resolve("brief.md"))).hasSize(120);
        }

        private static String previewedBrief(TestHome home, String spec) {
            return home.dispatchTask().previewBrief(TaskReference.parse("fit-lamp"), new DispatchRequest(CategoryId.IMPLEMENTATION, spec, List.of(), null));
        }

        private static String buildInstructions(int lines) {
            return "Build instruction.\n".repeat(lines).strip();
        }

        private static void dispatch(TestHome home, String spec) {
            home.dispatchTask().dispatch(TaskReference.parse("fit-lamp"), new DispatchRequest(CategoryId.IMPLEMENTATION, spec, List.of(), null),
                    CallingSession.UNNAMED, Actor.PRIMARY);
        }

        private static long linesOutsideTheTasksOwnSections(String brief) {
            List<String> lines = brief.lines().toList();
            int intentHeading = lines.indexOf("## Captain's intent");
            int specHeading = lines.indexOf("## Spec");
            int projectRulesHeading = lines.indexOf("## Project rules");
            int intentLines = specHeading - intentHeading - 3;
            int specLines = projectRulesHeading - specHeading - 3;
            return lines.size() - intentLines - specLines;
        }

        private static long sizeWhenPresent(Path file) throws IOException {
            return Files.exists(file) ? Files.size(file) : 0;
        }
    }

    @Nested
    @DisplayName("I-36 Fleet-sized data never travels as a command-line argument")
    class FleetSizedDataNeverTravelsAsAnArgument {

        private static final long TWO_MEGABYTES = 2_000_000;
        private static final int CHARTED_ON_THE_BOARD = 20;
        private static final int BOARD_DOCUMENT_CEILING = 16_384;
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
        @Tag("adapter")
        @DisplayName("AT-36 the sitrep board renders from a 2 MB backlog fixture")
        void theSitrepBoardRendersFromALargeBacklog() throws IOException {
            TestHome home = ReportGoldenCases.homeIn(directory);
            ReportGoldenCases.queuedBacklog(home, ReportGoldenCases.TWO_MEGABYTE_TASKS, ReportGoldenCases.TWO_MEGABYTE_NOTE_WORDS);

            ConsoleSitrep board = PresenterFixtures.sitrepConsolePresenter().present(home.fleetSnapshot().snapshot(), TestHome.START, ManualClock.UTC, "laptop", CaptainAddress.DEFAULT,
                    Presence.presentSince(TestHome.START), _ -> Map.of());

            assertThat(charactersOfTheBacklog(home)).isGreaterThanOrEqualTo(TWO_MEGABYTES);
            assertThat(board.chartedTotal()).isEqualTo(ReportGoldenCases.TWO_MEGABYTE_TASKS);
            assertThat(board.charted()).hasSize(CHARTED_ON_THE_BOARD).first().extracting(ConsoleSitrep.Charted::title).isEqualTo("Queued work item 1");
            assertThat(new JsonMapper().writeValueAsString(board)).hasSizeLessThan(BOARD_DOCUMENT_CEILING);
        }

        private static long charactersOfTheBacklog(TestHome home) {
            return home.jdbc().sql("select sum(length(title) + length(notes)) from task").query(Long.class).single();
        }
    }
}
