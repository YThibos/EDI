package io.github.ythibos.edi.application.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TestProcesses;
import io.github.ythibos.edi.testing.WorkerFixtures;

class StopTheCoreTest {

    @TempDir
    private Path directory;

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    private TestHome home;
    private Process pane;
    private Worker worker;

    @BeforeEach
    void prepareAHomeWithARunningWorker() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        pane = processes.register(new ProcessBuilder("sleep", "30").start());
        worker = WorkerFixtures.servingWorkerIn(home.runtime().openWindowOf(pane.pid(), ProcessHandle.current().pid()), home,
                WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
    }

    @Test
    void staysUpWhileAWorkerRunsUnlessTheCaptainHasTheWorkersStopped() {
        CallingSession session = session("Stop everything, the workers too.");

        assertThatThrownBy(() -> home.stopTheCore().stopFor(CoreStop.WHEN_NO_WORKER_RUNS, session, Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessageContaining("1 workers are running (fit-lamp#1)");
        assertThatThrownBy(() -> home.stopTheCore().stopFor(CoreStop.WITH_ITS_WORKERS, session, Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessageContaining("To stop the workers that are running, EDI needs the captain's word");

        home.captainsWord().record(session, "Stop everything, the workers too.", null, Actor.PRIMARY);

        assertThat(home.stopTheCore().stopFor(CoreStop.WITH_ITS_WORKERS, session, Actor.PRIMARY)).extracting(Worker::id).containsExactly(worker.id());
        assertThat(home.eventTypesOf(worker.id().task())).contains("WorkerStopRequested");
        assertThat(TestProcesses.awaitExit(pane.toHandle())).isTrue();
    }

    @Test
    void stopsWhenTheOnlyWorkersLeftHaveNoLiveWindow() {
        WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-door", "harness", "HRN-6"), CategoryId.IMPLEMENTATION);
        home.runtime().closeEveryWindow();

        assertThat(home.stopTheCore().stopFor(CoreStop.WHEN_NO_WORKER_RUNS, CallingSession.UNNAMED, Actor.PRIMARY)).isEmpty();
        assertThat(home.eventTypesOf(worker.id().task())).doesNotContain("WorkerStopRequested");
        pane.destroyForcibly();
        assertThat(TestProcesses.awaitExit(pane.toHandle())).isTrue();
    }

    @Test
    void restartsWithoutStoppingAnyWorker() {
        assertThat(home.stopTheCore().stopFor(CoreStop.RESTART, CallingSession.UNNAMED, Actor.PRIMARY)).isEmpty();
        assertThat(home.eventTypesOf(worker.id().task())).doesNotContain("WorkerStopRequested");
        assertThat(pane.isAlive()).isTrue();
        pane.destroyForcibly();
        assertThat(TestProcesses.awaitExit(pane.toHandle())).isTrue();
    }

    private CallingSession session(String typed) {
        UUID session = UUID.randomUUID();
        home.primaryHooks().sessionStarted(session, null);
        home.primaryHooks().promptSubmitted(session, typed);
        return new CallingSession(session);
    }
}
