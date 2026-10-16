package io.github.ythibos.edi.application.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class ObservePipelinesTest {

    private static final TaskId LAMP = new TaskId("fit-lamp");

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void createTheHome() throws IOException {
        home = WorkerFixtures.homeWithProjects(directory);
    }

    @Test
    void recordsARunWhenItIsFirstSeenAndWhenItEnds() {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        home.pipeline().runs(dispatched.launched().branch(), new PipelineRun("01RUN", "running", "review", "1111", null));

        home.observePipelines().observe();
        home.pipeline().runs(dispatched.launched().branch(), new PipelineRun("01RUN", "running", "test", "1111", null));
        home.observePipelines().observe();
        home.observePipelines().observe();
        home.pipeline().runs(dispatched.launched().branch(), new PipelineRun("01RUN", "completed", null, "1111", "https://github.com/example-owner/harness/pull/1"));
        home.observePipelines().observe();

        assertThat(home.eventTypesOf(LAMP)).containsSubsequence("PipelineRunSeen", "PipelineRunFinished").containsOnlyOnce("PipelineRunSeen");
        assertThat(home.pipelineRuns().latestOf(LAMP)).map(PipelineRun::status).contains("completed");
    }

    @Test
    void keepsAnIdleWorkerWhosePipelineRunsFromBeingStale() {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        Worker worker = dispatched.launched().worker();
        home.workerHooks().sessionStarted(worker.id(), worker.session(), null);
        home.pipeline().runs(dispatched.launched().branch(), new PipelineRun("01RUN", "running", "ci", "1111", null));
        home.observePipelines().observe();

        home.clock().advance(Duration.ofMinutes(40));
        home.judgeWorkers().judge();
        assertThat(home.eventTypesOf(LAMP)).doesNotContain("WorkerStale");

        home.pipeline().runs(dispatched.launched().branch(), new PipelineRun("01RUN", "failed", null, "1111", null));
        home.observePipelines().observe();
        home.judgeWorkers().judge();
        assertThat(home.eventTypesOf(LAMP)).contains("WorkerStale");
    }

    @Test
    void watchesNoPipelineOfWorkThatNeverRunsOne() {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3", DeliveryMode.LOCAL_BRANCH));
        home.pipeline().runs(dispatched.launched().branch(), new PipelineRun("01RUN", "running", "review", "1111", null));

        home.observePipelines().observe();

        assertThat(home.pipelineRuns().latestOf(LAMP)).isEmpty();
    }
}
