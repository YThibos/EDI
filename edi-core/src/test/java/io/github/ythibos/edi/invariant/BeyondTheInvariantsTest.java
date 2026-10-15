package io.github.ythibos.edi.invariant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.migration.MigrationNote;
import io.github.ythibos.edi.application.migration.MigrationPlan;
import io.github.ythibos.edi.application.migration.SetAsideSection;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.web.migration.MigrationPlanText;

@DisplayName("Acceptance tests beyond the invariants (12.7 and 17.4)")
class BeyondTheInvariantsTest {

    private static final Path OLD_HOME_FIXTURE = Path.of("src/test/resources/fork-home");

    @TempDir
    private Path directory;

    @Test
    @DisplayName("AT-40 a deliberately leaked child process in a test fails the build")
    void aLeakedChildProcessFailsTheBuild() {
        Pending.until(Slice.S3);
    }

    @Test
    @Tag("adapter")
    @DisplayName("AT-41 the migration dry run on a copy of the old home reproduces its counts and reports every unmapped item")
    void theMigrationDryRunReproducesTheCounts() throws IOException {
        Path oldHome = copyOf(OLD_HOME_FIXTURE);
        Map<Path, byte[]> before = contentsOf(oldHome);

        MigrationPlan plan = TestHome.in(directory.resolve("edi-home")).forkMigrationPlanner().plan(oldHome);

        assertThat(countsByState(plan.tasks())).containsExactly(
                Map.entry(TaskState.QUEUED, 8L), Map.entry(TaskState.IN_FLIGHT, 3L), Map.entry(TaskState.DONE, 4L));
        assertThat(plan.tasks().stream().collect(Collectors.groupingBy(task -> task.intake().kind(), Collectors.counting())))
                .containsExactlyInAnyOrderEntriesOf(Map.of(TaskKind.SHIP, 9L, TaskKind.SCOUT, 1L, TaskKind.CAPTAIN, 5L));
        assertThat(plan.tasks().stream().filter(Task::isHeld))
                .extracting(task -> task.id().value(), task -> task.hold().kind(), task -> task.hold().until())
                .containsExactly(
                        tuple("tide-tables", HoldKind.DEFERRAL, HoldUntil.captainsWord()),
                        tuple("beacon-colour", HoldKind.DECISION, HoldUntil.captainsWord()),
                        tuple("visit-day", HoldKind.DEFERRAL, HoldUntil.day(LocalDate.of(2026, 11, 1))));
        assertThat(plan.decisions().stream().filter(Decision::isOpen)).extracting(Decision::number).containsExactly(new DecisionNumber(57));
        assertThat(plan.decisions().stream().filter(decision -> !decision.isOpen()))
                .extracting(Decision::number, decision -> decision.answer().option(), decision -> decision.answer().channel())
                .containsExactly(
                        tuple(new DecisionNumber(55), new OptionKey('A'), AnswerChannel.CHAT),
                        tuple(new DecisionNumber(56), null, AnswerChannel.BOARD));
        assertThat(plan.decisionSequence().highest()).isEqualTo(57);
        assertThat(plan.decisionSequence().firstNewNumber()).isEqualTo(new DecisionNumber(58));
        assertThat(plan.highestEdiKey()).isEqualTo(WorkKey.parse("EDI-7"));
        assertThat(plan.setAside()).containsExactly(
                new SetAsideSection("Captain's Call (manual holds, all resolved)", List.of("old-call-one", "old-call-two")));
        assertThat(plan.notes()).extracting(MigrationNote::subject, MigrationNote::note).containsExactly(
                tuple("fog-horn", "appears again on line 30 of the backlog; only its first entry is imported"),
                tuple("gull-survey", "is blocked by bird-census, which is not in the backlog; the dependency is dropped"),
                tuple("loose-end", "names no project; the captain sets one"),
                tuple("relay-mast", "is not imported: its kind secondmate has no counterpart in EDI"),
                tuple("boat-paint", "has a hold of kind external, which is not a hold for the captain; it is not carried"),
                tuple("Gate-Repair", "is not imported: A task id is a kebab-case slug of at most 64 characters, such as fix-login-timeout: Gate-Repair"),
                tuple("D54", "on silent-choice was settled, but no answer was recorded; it is not carried"),
                tuple("Backlog line 32", "is not a task entry and is not imported: This line is not a task entry."));
        assertThat(new MigrationPlanText().text(plan)).contains(
                "Tasks to import (15)", "in-flight  3 (2 held)", "queued     8 (1 held)", "done       4",
                "open      D57", "answered  D55, D56", "the first new decision is D58", "the next is EDI-8", "For the captain (8)");
        assertThat(contentsOf(oldHome)).as("the old home after the dry run").containsExactlyInAnyOrderEntriesOf(before);
    }

    @Test
    @DisplayName("AT-42 the report presenter matches the old renderer on every ported golden case and the 2 MB backlog case")
    void theReportMatchesTheGoldenCases() {
        Pending.until(Slice.S2);
    }

    @Test
    @DisplayName("AT-46 presence switches to quiet without input, stays silent on routine wakes, never turns silence into away, and demo holds present")
    void presenceFollowsTheCaptain() {
        Pending.until(Slice.S8);
    }

    private Path copyOf(Path fixture) throws IOException {
        Path copy = directory.resolve("old-home");
        try (Stream<Path> files = Files.walk(fixture)) {
            for (Path file : files.toList()) {
                Path target = copy.resolve(fixture.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(file, target);
                }
            }
        }
        return copy;
    }

    private static Map<Path, byte[]> contentsOf(Path home) throws IOException {
        try (Stream<Path> files = Files.walk(home)) {
            return files.filter(Files::isRegularFile).collect(Collectors.toMap(Function.identity(), BeyondTheInvariantsTest::bytesOf));
        }
    }

    private static byte[] bytesOf(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }

    private static Map<TaskState, Long> countsByState(List<Task> tasks) {
        return tasks.stream().collect(Collectors.groupingBy(Task::state, () -> new EnumMap<>(TaskState.class), Collectors.counting()));
    }
}
