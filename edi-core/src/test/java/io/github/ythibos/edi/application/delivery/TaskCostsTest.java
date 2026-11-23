package io.github.ythibos.edi.application.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.delivery.TaskCost;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.LoginName;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerLaunch;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class TaskCostsTest {

    private static final Route SONNET_HIGH = new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.HIGH);
    private static final Route OPUS_XHIGH = new Route(new ModelId("claude-opus-5-5"), EffortLevel.XHIGH);

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void aHomeWithProjects() throws IOException {
        home = WorkerFixtures.homeWithProjects(directory);
    }

    @Test
    void recordsTheRouteTheTaskWasDispatchedOnWhenALaterWorkerRunsOnAnother() {
        Worker dispatched = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        home.workers().insert(Worker.launched(dispatched.id().next(), UUID.randomUUID(), CategoryId.IMPLEMENTATION, OPUS_XHIGH, home.runtime().openWindow(),
                new WorkerLaunch(LaunchKind.FRESH, TestHome.START.plusSeconds(60), home.machineBoot().current(), LoginName.DEFAULT)), "digest-of-fit-lamp-2");

        TaskCost cost = home.costCounting().countFor(home.workerContext().of(new TaskId("fit-lamp")));

        assertThat(dispatched.route()).isEqualTo(SONNET_HIGH);
        assertThat(cost.route()).isEqualTo(SONNET_HIGH);
    }
}
