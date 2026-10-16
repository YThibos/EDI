package io.github.ythibos.edi.application.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.testing.TestHome;

@Tag("adapter")
class ForkMigrationPlannerTest {

    @TempDir
    private Path directory;

    @Test
    void readsATaskWithItsKeyProjectsDatesAndNotes() throws IOException {
        MigrationPlan plan = planFor("""
                ## Done
                - [x] lamp - HRN-4 Choose the lamp (repo: harbour) (kind: captain) (done 2026-10-05)
                  A note.
                  Captain decision:
                  Captain (2026-10-05): "D3, B"
                  Answer: B - oil.

                  Captain decision id: D3
                  Captain decision card: {"title":"Which lamp?","context":"","projects":["lighthouse","harbour"],"options":[{"key":"A","text":"LED"},{"key":"B","text":"Oil"}],"recommend":{"key":"A","why":"lasts"}}
                """);

        Task lamp = plan.tasks().getFirst();
        assertThat(lamp.intake().workKey()).isEqualTo(WorkKey.parse("HRN-4"));
        assertThat(lamp.intake().projects()).containsExactly(new ProjectName("lighthouse"), new ProjectName("harbour"));
        assertThat(lamp.created()).isEqualTo(Instant.parse("2026-10-05T00:00:00Z"));
        assertThat(lamp.closed()).isEqualTo(Instant.parse("2026-10-05T00:00:00Z"));
        assertThat(lamp.notes()).startsWith("A note.\nCaptain decision:");
        assertThat(plan.decisions().getFirst().answer().interpretation()).isEqualTo("B - oil.");
        assertThat(plan.notes()).isEmpty();
    }

    @Test
    void reportsEveryDecisionAndLinkItCannotCarry() throws IOException {
        MigrationPlan plan = planFor("""
                ## Queued
                - [ ] card-alone - A card without a number (repo: harbour) (since 2026-08-01)
                  Captain decision card: {"title":"T","options":[{"key":"A","text":"x"}],"recommend":{"key":"A","why":"y"}}
                - [ ] broken-card - A card that is not JSON (repo: harbour) (since 2026-08-01)
                  Captain decision id: D5
                  Captain decision card: {not json}
                - [ ] odd-card - A card with no recommendation (repo: harbour) (since 2026-08-01)
                  Captain decision id: D6
                  Captain decision card: {"title":"T","options":[{"key":"A","text":"x"}]}
                - [ ] child - A child (repo: harbour) (since 2026-08-01) parent: card-alone
                - [ ] spaced - A project name with a space (repo: two words) (since 2026-08-01)
                """);

        assertThat(plan.tasks()).hasSize(5);
        assertThat(plan.decisions()).isEmpty();
        assertThat(plan.notes()).extracting(MigrationNote::subject).containsExactly("card-alone", "D5", "D6", "child", "spaced");
        assertThat(plan.notes().get(1).note()).startsWith("on broken-card has no readable card (");
        assertThat(plan.notes().get(2).note()).isEqualTo("on odd-card has a card EDI cannot carry: it recommends no option");
        assertThat(plan.notes()).extracting(MigrationNote::subject, MigrationNote::note).contains(
                tuple("child", "has a parent link to card-alone, which EDI does not carry"),
                tuple("spaced", "names the project \"two words\", which is not a project name EDI accepts; it is dropped"));
    }

    @Test
    void dropsADependencyOnItselfOrOnAnEntryItCannotImport() throws IOException {
        MigrationPlan plan = planFor("""
                ## Queued
                - [ ] Odd-Id - An entry EDI cannot import (repo: harbour)
                - [ ] waiting - Waits on the odd one blocked-by: Odd-Id blocked-by: waiting (repo: harbour)
                """);

        assertThat(plan.tasks()).extracting(task -> task.id().value()).containsExactly("waiting");
        assertThat(plan.tasks().getFirst().intake().blockedBy()).isEmpty();
        assertThat(plan.notes()).extracting(MigrationNote::subject, MigrationNote::note).containsExactly(
                tuple("Odd-Id", "is not imported: A task id is a kebab-case slug of at most 64 characters, such as fix-login-timeout: Odd-Id"),
                tuple("waiting", "is blocked by Odd-Id, which is not imported; the dependency is dropped"),
                tuple("waiting", "is blocked by itself; the dependency is dropped"));
    }

    @Test
    void datesAnUndatedEntryAtTheMigration() throws IOException {
        MigrationPlan plan = planFor("""
                ## Queued
                - [ ] undated - No date at all (repo: harbour)
                """);

        assertThat(plan.tasks().getFirst().created()).isEqualTo(TestHome.START);
        assertThat(plan.highestEdiKey()).isNull();
    }

    private MigrationPlan planFor(String backlog) throws IOException {
        Path oldHome = directory.resolve("old-home");
        Files.createDirectories(oldHome.resolve("data"));
        Files.writeString(oldHome.resolve("data/backlog.md"), backlog);
        return TestHome.in(directory.resolve("edi-home")).forkMigrationPlanner().plan(oldHome);
    }
}
