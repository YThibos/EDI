package io.github.ythibos.edi.application.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.PaneState;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class LaunchWatchTest {

    @TempDir
    private Path directory;

    @Test
    void reportsALaunchWithNoHookAfterTwoMinutesOnceWithWhatThePaneShows() throws IOException {
        TestHome home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        Worker silent = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        Worker started = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-horn", "harness", "HRN-4"), CategoryId.IMPLEMENTATION);
        home.workerHooks().sessionStarted(started.id(), started.session(), null);
        home.runtime().showing(PaneState.DIALOG_TRUST);

        home.clock().advance(Duration.ofMinutes(2).minusSeconds(1));
        assertThat(home.noticeMissedLaunches().notice()).isEmpty();
        home.clock().advance(Duration.ofSeconds(1));
        assertThat(home.noticeMissedLaunches().notice()).extracting(Worker::id).containsExactly(silent.id());
        home.clock().advance(Duration.ofMinutes(10));
        assertThat(home.noticeMissedLaunches().notice()).isEmpty();

        assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).containsOnlyOnce("WorkerLaunchFailed");
        assertThat(home.jdbc().sql("select json_extract(payload, '$.pane') from event where type = 'WorkerLaunchFailed'").query(String.class).single())
                .isEqualTo("dialog-trust");
        assertThat(home.eventTypesOf(new TaskId("fit-horn"))).containsExactly("WorkerStarted");
    }
}
