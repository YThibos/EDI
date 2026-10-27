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
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.worker.DoneCheck;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class ScoutDeliveryTest {

    private static final TaskId CAVES = new TaskId("map-caves");

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void createTheHome() throws IOException {
        home = WorkerFixtures.homeWithProjects(directory);
    }

    @Test
    void refusesADoneWithoutItsReportNamingThePath() {
        DispatchOutcome dispatched = scout();

        assertThatThrownBy(() -> done(dispatched)).isInstanceOf(Refusal.class)
                .hasMessageContaining("the report " + report() + " does not exist, and a scout delivers its findings there");
        assertThat(home.workers().get(dispatched.launched().worker().id()).orElseThrow().doneCheck()).isNull();
        assertThat(home.eventTypesOf(CAVES)).endsWith("DeliveryRefused");
    }

    @Test
    void acceptsADoneOnceItsReportExists() throws IOException {
        DispatchOutcome dispatched = scout();
        Files.writeString(report(), "The caves run north.\n");

        assertThat(done(dispatched).doneCheck()).isEqualTo(DoneCheck.ACCEPTED);
    }

    private DispatchOutcome scout() {
        return DeliveryFixtures.dispatched(home, WorkerFixtures.queued(CAVES.value(), TaskKind.SCOUT, "harness", "HRN-5"));
    }

    private Path report() {
        return home.home().taskDirectory(CAVES).resolve("report.md");
    }

    private Worker done(DispatchOutcome dispatched) {
        return home.recordStatus().report(dispatched.launched().worker().id(), StatusState.DONE, null, null, "The caves are mapped.");
    }
}
