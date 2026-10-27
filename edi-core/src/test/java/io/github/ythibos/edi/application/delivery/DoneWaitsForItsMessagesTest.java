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

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class DoneWaitsForItsMessagesTest {

    private static final TaskId SHELF = new TaskId("move-shelf");
    private static final TaskId SURVEY = new TaskId("survey-lamps");

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void createTheHome() throws IOException {
        home = WorkerFixtures.homeWithProjects(directory);
    }

    @Test
    void refusesADoneWhileASteerWaitsUnacknowledgedAndLeavesTheStatusAsItWas() throws IOException {
        Worker worker = shipWithItsReport();
        report(worker, StatusState.WORKING, "Moving the shelf.");
        steer(SHELF, "Move the lamp along with the shelf.");

        assertThatThrownBy(() -> report(worker, StatusState.DONE, "The shelf is moved.")).isInstanceOf(Refusal.class)
                .hasMessageContaining("message 1 waits unacknowledged")
                .hasMessageContaining("edi inbox ack 1");
        assertThat(home.workers().get(worker.id()).orElseThrow().lastStatus().report().state()).isEqualTo(StatusState.WORKING);
        assertThat(home.tasks().get(SHELF).state()).isEqualTo(TaskState.IN_FLIGHT);
        assertThat(home.eventTypesOf(SHELF)).endsWith("DeliveryRefused");
    }

    @Test
    void acceptsTheSameDoneOnceTheSteerIsAcknowledged() throws IOException {
        Worker worker = shipWithItsReport();
        steer(SHELF, "Move the lamp along with the shelf.");
        assertThatThrownBy(() -> report(worker, StatusState.DONE, "The shelf is moved.")).isInstanceOf(Refusal.class);

        home.workerInbox().acknowledge(worker.id(), 1);
        report(worker, StatusState.DONE, "The shelf is moved.");

        assertThat(home.tasks().get(SHELF).state()).isEqualTo(TaskState.DONE);
    }

    @Test
    void namesEveryMessageThatWaitsUnacknowledged() throws IOException {
        Worker worker = shipWithItsReport();
        steer(SHELF, "Move the lamp along with the shelf.");
        steer(SHELF, "Then dust the shelf.");
        steer(SHELF, "Then oil the hinges.");
        home.workerInbox().read(worker.id());
        home.workerInbox().acknowledge(worker.id(), 2);

        assertThatThrownBy(() -> report(worker, StatusState.DONE, "The shelf is moved.")).isInstanceOf(Refusal.class)
                .hasMessageContaining("messages 1 and 3 wait unacknowledged")
                .hasMessageContaining("edi inbox ack <n>");
    }

    @Test
    void holdsAScoutsDoneToTheSameRule() {
        Worker scout = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queued(SURVEY.value(), TaskKind.SCOUT, "harness", "HRN-4"),
                CategoryId.RESEARCH);
        steer(SURVEY, "Survey the oil lamps too.");

        assertThatThrownBy(() -> report(scout, StatusState.DONE, "The survey is in the report.")).isInstanceOf(Refusal.class)
                .hasMessageContaining("message 1 waits unacknowledged");

        home.workerInbox().acknowledge(scout.id(), 1);
        Worker reported = report(scout, StatusState.DONE, "The survey is in the report.");

        assertThat(reported.lastStatus().report().state()).isEqualTo(StatusState.DONE);
    }

    private Worker shipWithItsReport() throws IOException {
        Worker worker = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip(SHELF.value(), "harness", "HRN-8", DeliveryMode.REPORT))
                .launched()
                .worker();
        Files.writeString(home.home().taskDirectory(SHELF).resolve("report.md"), "Moved the shelf; the shelf check passes.\n");
        return worker;
    }

    private Worker report(Worker worker, StatusState state, String text) {
        return home.recordStatus().report(worker.id(), state, null, null, text);
    }

    private void steer(TaskId task, String text) {
        home.steerWorker().steer(TaskReference.parse(task.value()), text, null, Actor.PRIMARY);
    }
}
