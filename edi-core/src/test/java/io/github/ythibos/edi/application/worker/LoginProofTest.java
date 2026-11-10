package io.github.ythibos.edi.application.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.store.LoggedEvent;
import io.github.ythibos.edi.adapter.store.WakeStore.QueuedWake;
import io.github.ythibos.edi.application.port.WorkerRuntime.LoginMismatch;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerRanOnAnotherLogin;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.wake.Wake;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TestProcesses;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class LoginProofTest {

    private static final TaskId LAMP = new TaskId("fit-lamp");
    private static final String TRANSCRIPT = "/logins/hobby/projects/-pool-1-lamp/0b9a3c1e.jsonl";
    private static final LoginMismatch ON_THE_HOBBY_LOGIN = new LoginMismatch(Path.of("/logins/hobby"), Path.of("/home/keeper/.claude"));

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    private TestHome home;
    private Process pane;
    private Worker lamp;

    @BeforeEach
    void serveAWorkerInARealPane() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        pane = processes.register(new ProcessBuilder("sleep", "30").start());
        lamp = WorkerFixtures.servingWorkerIn(home.runtime().openWindowOf(pane.pid(), ProcessHandle.current().pid()), home,
                WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
    }

    @Test
    void stopsAWorkerWhoseFirstHookReportsATranscriptOutsideItsRecordedLoginAndSaysWhereItRanAndShouldHave() {
        home.runtime().runsEverySessionIn(ON_THE_HOBBY_LOGIN);

        home.workerHooks().sessionStarted(lamp.id(), lamp.session(), TRANSCRIPT);

        assertThat(TestProcesses.awaitExit(pane.toHandle())).isTrue();
        assertThat(home.eventTypesOf(LAMP)).containsSubsequence("WorkerStarted", "WorkerRanOnAnotherLogin", "WorkerStopRequested");
        LoggedEvent ran = home.events().ofTask(LAMP).stream().filter(event -> event.isA(WorkerRanOnAnotherLogin.class)).findFirst().orElseThrow();
        assertThat(ran.text("login")).isEqualTo("default");
        assertThat(ran.text("ranIn")).isEqualTo("/logins/hobby");
        assertThat(ran.text("shouldHaveRunIn")).isEqualTo("/home/keeper/.claude");
        assertThat(home.wakes().unacknowledged()).extracting(QueuedWake::wake).filteredOn(wake -> wake.kind() == WakeKind.ON_ANOTHER_LOGIN)
                .singleElement().extracting(Wake::detail).isEqualTo("/logins/hobby instead of the Claude login default's /home/keeper/.claude");
    }

    @Test
    void leavesAWorkerRunningWhoseTranscriptLiesUnderItsRecordedLogin() {
        home.workerHooks().sessionStarted(lamp.id(), lamp.session(), TRANSCRIPT);

        assertThat(pane.isAlive()).isTrue();
        assertThat(home.eventTypesOf(LAMP)).contains("WorkerStarted").doesNotContain("WorkerRanOnAnotherLogin", "WorkerStopRequested");
        endThePane();
    }

    @Test
    void provesTheLoginOnTheFirstHookOnly() {
        home.workerHooks().sessionStarted(lamp.id(), lamp.session(), TRANSCRIPT);
        home.runtime().runsEverySessionIn(ON_THE_HOBBY_LOGIN);

        home.workerHooks().sessionStarted(lamp.id(), lamp.session(), TRANSCRIPT);

        assertThat(pane.isAlive()).isTrue();
        assertThat(home.eventTypesOf(LAMP)).doesNotContain("WorkerRanOnAnotherLogin", "WorkerStopRequested");
        endThePane();
    }

    private void endThePane() {
        pane.destroyForcibly();
        assertThat(TestProcesses.awaitExit(pane.toHandle())).isTrue();
    }
}
