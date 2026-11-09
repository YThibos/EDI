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
import io.github.ythibos.edi.application.fleet.ChangingRequest;
import io.github.ythibos.edi.application.fleet.WaitingOnTheCaptain;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.status.StatusState;
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
        home.pipeline().runs(dispatched.launched().branch(), new PipelineRun("01RUN", "running", "review", "1111", null, false));

        home.observePipelines().observe();
        home.pipeline().runs(dispatched.launched().branch(), new PipelineRun("01RUN", "running", "test", "1111", null, false));
        home.observePipelines().observe();
        home.observePipelines().observe();
        home.pipeline().runs(dispatched.launched().branch(), new PipelineRun("01RUN", "completed", null, "1111", "https://github.com/example-owner/harness/pull/1", false));
        home.observePipelines().observe();

        assertThat(home.eventTypesOf(LAMP)).containsSubsequence("PipelineRunSeen", "PipelineRunFinished").containsOnlyOnce("PipelineRunSeen");
        assertThat(home.pipelineRuns().latestOf(LAMP)).map(PipelineRun::status).contains("completed");
    }

    @Test
    void recordsTheMergeRequestOnceARunChangesAfterItWasOpened() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        home.pipeline().runs(dispatched.launched().branch(), new PipelineRun("01RUN", "running", "review", "1111", null, false));
        home.observePipelines().observe();
        assertThat(home.deliveries().of(LAMP)).isEmpty();

        MergeRequestView draft = WorkerFixtures.opensTheDraftOf(home, dispatched.launched().worker());
        home.pipeline().runs(dispatched.launched().branch(), new PipelineRun("01RUN", "running", "ci", "1111", draft.address().url(), false));
        home.observePipelines().observe();
        home.pipeline().runs(dispatched.launched().branch(), new PipelineRun("01RUN", "completed", null, "1111", draft.address().url(), false));
        home.observePipelines().observe();

        assertThat(home.deliveries().of(LAMP)).map(Delivery::address).contains(draft.address());
        assertThat(home.eventTypesOf(LAMP)).containsOnlyOnce("DraftRecorded");
    }

    @Test
    void seesARunningPipelineFinishAfterTheDoneIsAccepted() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        MergeRequestView draft = WorkerFixtures.opensTheDraftOf(home, dispatched.launched().worker());
        home.pipeline().runs(dispatched.launched().branch(), new PipelineRun("01RUN", "running", "ci", "1111", draft.address().url(), false));
        home.observePipelines().observe();
        Worker handedOver = home.recordStatus().report(dispatched.launched().worker().id(), StatusState.DONE, null, null, "Delivered.");
        WaitingOnTheCaptain changing = home.fleetSnapshot().snapshot().waiting();

        home.pipeline().runs(dispatched.launched().branch(), new PipelineRun("01RUN", "completed", null, "1111", draft.address().url(), false));
        home.observePipelines().observe();
        WaitingOnTheCaptain finished = home.fleetSnapshot().snapshot().waiting();

        assertThat(handedOver.hadItsDoneAccepted()).isTrue();
        assertThat(changing.changing()).extracting(request -> request.task().id()).containsExactly(LAMP);
        assertThat(home.pipelineRuns().latestOf(LAMP)).map(PipelineRun::status).contains("completed");
        assertThat(finished.changing()).isEmpty();
        assertThat(finished.reviews()).extracting(ready -> ready.task().id()).containsExactly(LAMP);
    }

    @Test
    void seesAFinishedRequestReadyWhileItsPipelineWatchesItsPassedChecksThroughEveryReArmUntilAFixStarts() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        MergeRequestView draft = WorkerFixtures.opensTheDraftOf(home, dispatched.launched().worker());
        PipelineRun watching = new PipelineRun("01RUN", "running", "ci", "1111", draft.address().url(), false);
        home.pipeline().runs(dispatched.launched().branch(), watching);
        home.observePipelines().observe();
        home.recordStatus().report(dispatched.launched().worker().id(), StatusState.DONE, null, null, "Delivered.");
        WaitingOnTheCaptain pending = home.fleetSnapshot().snapshot().waiting();

        home.pipeline().runs(dispatched.launched().branch(), watching.withChecksPassed(true));
        home.observePipelines().observe();
        WaitingOnTheCaptain passed = home.fleetSnapshot().snapshot().waiting();
        home.clock().advance(Duration.ofHours(1));
        home.observePipelines().observe();
        WaitingOnTheCaptain reArmed = home.fleetSnapshot().snapshot().waiting();
        home.pipeline().runs(dispatched.launched().branch(), watching);
        home.observePipelines().observe();
        WaitingOnTheCaptain fixing = home.fleetSnapshot().snapshot().waiting();

        assertThat(pending.changing()).extracting(request -> request.task().id()).containsExactly(LAMP);
        assertThat(passed.reviews()).extracting(ready -> ready.task().id()).containsExactly(LAMP);
        assertThat(passed.changing()).isEmpty();
        assertThat(reArmed.reviews()).extracting(ready -> ready.task().id()).containsExactly(LAMP);
        assertThat(reArmed.changing()).isEmpty();
        assertThat(fixing.reviews()).isEmpty();
        assertThat(fixing.changing()).extracting(ChangingRequest::whyChanging).containsExactly("pipeline ci");
    }

    @Test
    void keepsAnIdleWorkerWhosePipelineRunsFromBeingStale() {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        Worker worker = dispatched.launched().worker();
        home.workerHooks().sessionStarted(worker.id(), worker.session(), null);
        home.pipeline().runs(dispatched.launched().branch(), new PipelineRun("01RUN", "running", "ci", "1111", null, false));
        home.observePipelines().observe();

        home.clock().advance(Duration.ofMinutes(40));
        home.judgeWorkers().judge();
        assertThat(home.eventTypesOf(LAMP)).doesNotContain("WorkerStale");

        home.pipeline().runs(dispatched.launched().branch(), new PipelineRun("01RUN", "failed", null, "1111", null, false));
        home.observePipelines().observe();
        home.judgeWorkers().judge();
        assertThat(home.eventTypesOf(LAMP)).contains("WorkerStale");
    }

    @Test
    void watchesNoPipelineOfWorkThatNeverRunsOne() {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3", DeliveryMode.LOCAL_BRANCH));
        home.pipeline().runs(dispatched.launched().branch(), new PipelineRun("01RUN", "running", "review", "1111", null, false));

        home.observePipelines().observe();

        assertThat(home.pipelineRuns().latestOf(LAMP)).isEmpty();
    }
}
