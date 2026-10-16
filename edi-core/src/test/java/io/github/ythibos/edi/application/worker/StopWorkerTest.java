package io.github.ythibos.edi.application.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerHealth;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TestProcesses;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class StopWorkerTest {

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void prepareAHome() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
    }

    @Test
    void signalsTheProcessesItRecordedAndKeepsTheWorkerResumable() throws IOException {
        Process pane = processes.register(new ProcessBuilder("sleep", "30").start());
        Worker worker = WorkerFixtures.servingWorkerIn(home.runtime().openWindowOf(pane.pid(), ProcessHandle.current().pid()), home,
                WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);

        Worker stopped = home.stopWorker().stop(new TaskReference.ById(new TaskId("fit-lamp")), Actor.PRIMARY);

        assertThat(TestProcesses.awaitExit(pane.toHandle())).isTrue();
        assertThat(stopped.stopRequestedAt()).isEqualTo(home.clock().now());
        assertThat(home.workerList().judged(home.workers().get(worker.id()).orElseThrow()).health()).isInstanceOf(WorkerHealth.Stopped.class);
        assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).containsOnlyOnce("WorkerStopRequested");
    }

    @Test
    void refusesToSignalAProcessThatNoLongerBelongsToTheWorkersServer() throws IOException {
        Process stranger = processes.register(new ProcessBuilder("sleep", "30").start());
        WorkerFixtures.servingWorkerIn(home.runtime().openWindowOf(stranger.pid(), 1), home,
                WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);

        assertThatThrownBy(() -> home.stopWorker().stop(new TaskReference.ById(new TaskId("fit-lamp")), Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessageContaining("no longer belongs to fit-lamp#1's tmux server");
        assertThat(stranger.isAlive()).isTrue();
        stranger.destroyForcibly();
        assertThat(TestProcesses.awaitExit(stranger.toHandle())).isTrue();
    }

    @Test
    void refusesToStopAWorkerThatIsNotRunning() {
        WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        home.runtime().closeEveryWindow();

        assertThatThrownBy(() -> home.stopWorker().stop(new TaskReference.ById(new TaskId("fit-lamp")), Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessage("fit-lamp#1 is not running, so there is nothing to stop.");
    }
}
