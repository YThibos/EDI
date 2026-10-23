package io.github.ythibos.edi.application.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class ReportDeliveryTest {

    private static final TaskId SHELF = new TaskId("move-shelf");

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void createTheHome() throws IOException {
        home = WorkerFixtures.homeWithProjects(directory);
    }

    @Test
    void retiresTheTaskOnceItsReportExistsAndTheCopyHoldsNoChange() throws IOException {
        DispatchOutcome dispatched = reportDelivery();
        writeTheReport();

        done(dispatched);

        assertThat(home.tasks().get(SHELF).state()).isEqualTo(TaskState.DONE);
        assertThat(home.eventTypesOf(SHELF)).containsSubsequence("StatusReported", "TaskCostRecorded", "TaskCleanedUp");
        assertThat(dispatched.launched().worktree()).doesNotExist();
        assertThat(home.git().commitAt(home.home().root().resolve("projects/harness"), "refs/heads/" + dispatched.launched().branch())).isEmpty();
        assertThat(home.workers().get(dispatched.launched().worker().id()).orElseThrow().isRetired()).isTrue();
        assertThat(home.home().taskDirectory(SHELF).resolve("report.md")).isRegularFile();
    }

    @Test
    void refusesADoneWithoutItsReport() {
        DispatchOutcome dispatched = reportDelivery();

        assertThatThrownBy(() -> done(dispatched)).isInstanceOf(Refusal.class)
                .hasMessageContaining("the report " + home.home().taskDirectory(SHELF).resolve("report.md") + " does not exist");
        assertThat(home.tasks().get(SHELF).state()).isEqualTo(TaskState.IN_FLIGHT);
        assertThat(home.eventTypesOf(SHELF)).endsWith("DeliveryRefused");
    }

    @Test
    void refusesADoneWhoseCopyCarriesAChange() throws IOException {
        DispatchOutcome dispatched = reportDelivery();
        writeTheReport();
        FixtureRepository.at(dispatched.launched().worktree()).write("shelf.txt", "a shelf\n").commit("HRN-8 Fit the shelf").write("notes.txt", "notes\n");

        assertThatThrownBy(() -> done(dispatched)).isInstanceOf(Refusal.class)
                .hasMessageContaining("a report delivery carries no change, yet notes.txt is not committed and commit ")
                .hasMessageContaining("\"HRN-8 Fit the shelf\" is not in origin/main");
        assertThat(home.tasks().get(SHELF).state()).isEqualTo(TaskState.IN_FLIGHT);
        assertThat(dispatched.launched().worktree()).isDirectory();
    }

    @Test
    void briefsTheWorkerToDeliverItsReportAndCommitNothing() throws IOException {
        reportDelivery();

        assertThat(Files.readString(home.home().taskDirectory(SHELF).resolve("brief.md")))
                .contains("- Delivery: report", "This work is delivered by its report: commit nothing, push nothing",
                        "the copy holds no commit or change beyond `origin/main`, and then retires the task.")
                .doesNotContain("no-mistakes axi run");
        assertThat(home.home().taskDirectory(SHELF).resolve("intent.md")).doesNotExist();
    }

    private DispatchOutcome reportDelivery() {
        return DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip(SHELF.value(), "harness", "HRN-8", DeliveryMode.REPORT));
    }

    private void writeTheReport() throws IOException {
        Files.writeString(home.home().taskDirectory(SHELF).resolve("report.md"), "Moved the shelf; the shelf check passes.\n");
    }

    private void done(DispatchOutcome dispatched) {
        home.recordStatus().report(dispatched.launched().worker().id(), StatusState.DONE, null, null, "The shelf is moved.");
    }
}
