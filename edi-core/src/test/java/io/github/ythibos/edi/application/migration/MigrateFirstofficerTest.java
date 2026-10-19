package io.github.ythibos.edi.application.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.testing.Cards;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;
import io.github.ythibos.edi.web.migration.MigrationReportText;

@Tag("adapter")
class MigrateFirstofficerTest {

    private static final Path OLD_HOME_FIXTURE = Path.of("src/test/resources/fork-home");

    @TempDir
    private Path directory;

    @Test
    void leavesADecisionNumberEdiAlreadyUsesWithItsOwnTaskAndContinuesAfterTheLargerSequence() throws IOException {
        Path oldHome = copyOfTheOldHome();
        TestHome home = TestHome.in(directory.resolve("edi-home")).registering("projects:\n  - name: harbour\n    tracker: { kind: edi, projectKey: HRB }\n")
                .storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harbour", "HRB-1"))).continuingDecisionsFrom(56);
        home.raiseDecision().raise(TaskReference.parse("fit-lamp"), Cards.card("Which lamp?"), Actor.PRIMARY);
        home.continuingDecisionsFrom(80);

        MigrationReport dryRun = home.migrateFirstofficer().dryRun(oldHome);
        home.migrateFirstofficer().run(oldHome, true, Actor.PRIMARY);

        assertThat(dryRun.backlog().conflicts()).extracting(MigrationNote::subject, MigrationNote::note).containsExactly(
                tuple("D57", "is already EDI's decision on fit-lamp, so beacon-colour and its decision are not imported"));
        assertThat(dryRun.backlog()).extracting(BacklogSurvey::ediOwnTasks, BacklogSurvey::ediOwnDecisions)
                .containsExactly(List.of(new TaskId("fit-lamp")), List.of(new DecisionNumber(57)));
        assertThat(new MigrationReportText().text(dryRun)).contains(
                "Decision sequence: 57, the highest in data/decision-seq; EDI has already issued D80, so the first new decision is D81.",
                "EDI's own work, which the import leaves as it is", "D57                         is already EDI's decision on fit-lamp");
        assertThat(home.tasks().contains(new TaskId("beacon-colour"))).isFalse();
        assertThat(home.decisions().get(new DecisionNumber(57)).task()).isEqualTo(new TaskId("fit-lamp"));
        assertThat(home.decisions().lastNumberIssued()).isEqualTo(80);
        assertThat(home.tasks().contains(new TaskId("lantern-rework"))).isTrue();
    }

    @Test
    void refusesAnOldHomeThatOverlapsTheEdiHome() throws IOException {
        Path oldHome = copyOfTheOldHome();
        TestHome inside = TestHome.in(oldHome.resolve("edi"));

        assertThatThrownBy(() -> inside.migrateFirstofficer().dryRun(oldHome)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap; each must lie outside the other");
    }

    private Path copyOfTheOldHome() throws IOException {
        Path copy = directory.resolve("old-home");
        try (Stream<Path> files = Files.walk(OLD_HOME_FIXTURE)) {
            for (Path file : files.toList()) {
                Path target = copy.resolve(OLD_HOME_FIXTURE.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(file, target);
                }
            }
        }
        return copy;
    }
}
