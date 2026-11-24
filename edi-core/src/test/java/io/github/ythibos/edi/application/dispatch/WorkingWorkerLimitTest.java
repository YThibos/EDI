package io.github.ythibos.edi.application.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TestProcesses;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class WorkingWorkerLimitTest {

    private static final String MAVEN_LAUNCHER = "org.codehaus.plexus.classworlds.launcher.Launcher";
    private static final ProjectName HARNESS = new ProjectName("harness");

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    @Test
    void startsADispatchAtOnceWhenEveryWorkerOfTheProjectHasFinishedIsIdleOrWaitsOnAQuestion() throws IOException {
        TestHome home = harnessLimitedTo(3);
        home.storing(tasks("fit-lamp", "fit-horn", "fit-bell", "fit-lens"));
        Worker finished = dispatch(home, "fit-lamp").launched().worker();
        Worker idle = dispatch(home, "fit-horn").launched().worker();
        Worker asking = dispatch(home, "fit-bell").launched().worker();
        WorkerFixtures.opensTheDraftOf(home, finished);
        home.recordStatus().report(finished.id(), StatusState.DONE, null, null, "The lamp is fitted.");
        becomesBusy(home, idle);
        stopsItsTurn(home, idle);
        becomesBusy(home, asking);
        home.recordStatus().report(asking.id(), StatusState.NEEDS_DECISION, new StatusKey("bell-pitch"), null, "Which pitch?");

        DispatchOutcome lens = dispatch(home, "fit-lens");

        assertThat(lens.wasDeferred()).isFalse();
        assertThat(workingOnHarness(home)).containsExactly(lens.launched().worker().id());
    }

    @Test
    void doesNotCountAFinishedWorker() throws IOException {
        TestHome home = harnessLimitedTo(1);
        home.storing(tasks("fit-lamp"));
        Worker lamp = dispatch(home, "fit-lamp").launched().worker();
        WorkerFixtures.opensTheDraftOf(home, lamp);
        becomesBusy(home, lamp);
        assertThat(workingOnHarness(home)).containsExactly(lamp.id());

        home.recordStatus().report(lamp.id(), StatusState.DONE, null, null, "The lamp is fitted.");

        assertThat(workingOnHarness(home)).isEmpty();
    }

    @Test
    void doesNotCountAWorkerIdleAtItsPrompt() throws IOException {
        TestHome home = harnessLimitedTo(1);
        home.storing(tasks("fit-lamp", "fit-horn"));
        Worker lamp = dispatch(home, "fit-lamp").launched().worker();
        becomesBusy(home, lamp);
        assertThat(workingOnHarness(home)).containsExactly(lamp.id());

        stopsItsTurn(home, lamp);

        assertThat(workingOnHarness(home)).isEmpty();
        assertThat(dispatch(home, "fit-horn").wasDeferred()).isFalse();
    }

    @Test
    void doesNotCountAWorkerWaitingOnAQuestionAndCountsItAgainOnceItWorksOn() throws IOException {
        TestHome home = harnessLimitedTo(1);
        home.storing(tasks("fit-lamp"));
        Worker lamp = dispatch(home, "fit-lamp").launched().worker();
        becomesBusy(home, lamp);

        home.recordStatus().report(lamp.id(), StatusState.NEEDS_DECISION, new StatusKey("lamp-choice"), null, "Which lamp?");

        assertThat(workingOnHarness(home)).isEmpty();
        home.recordStatus().report(lamp.id(), StatusState.WORKING, null, null, "The captain chose the brass lamp.");
        assertThat(workingOnHarness(home)).containsExactly(lamp.id());
    }

    @Test
    void countsAWorkerAgainOnceAPromptReachesItAfterItsQuestionWithoutANewStatus() throws IOException {
        TestHome home = harnessLimitedTo(1);
        home.storing(tasks("fit-lamp", "fit-horn"));
        Worker lamp = dispatch(home, "fit-lamp").launched().worker();
        asksAQuestionAndEndsItsTurn(home, lamp);
        assertThat(workingOnHarness(home)).isEmpty();

        isAnsweredWithoutANewStatus(home, lamp);

        assertThat(workingOnHarness(home)).containsExactly(lamp.id());
        assertThat(dispatch(home, "fit-horn").deferred().working()).containsExactly(lamp.id());
    }

    @Test
    void keepsCountingAnAnsweredWorkerWhoseValidationPipelineRunsAfterItsTurn() throws IOException {
        TestHome home = harnessLimitedTo(1);
        home.storing(tasks("fit-lamp"));
        Worker lamp = dispatch(home, "fit-lamp").launched().worker();
        asksAQuestionAndEndsItsTurn(home, lamp);
        isAnsweredWithoutANewStatus(home, lamp);
        home.pipelineRuns().save(lamp.id().task(), new PipelineRun("01RUN", "running", "test", false, "abc123", null, false), TestHome.START);

        stopsItsTurn(home, lamp);

        assertThat(workingOnHarness(home)).containsExactly(lamp.id());
    }

    @Test
    void keepsCountingAnAnsweredWorkerOnABuildRunAfterItsTurn() throws IOException {
        TestHome home = harnessLimitedTo(1);
        Process maven = processes.register(new ProcessBuilder("sh", "-c", "sleep 30; :", MAVEN_LAUNCHER, "-B", "verify").start());
        Worker lamp = WorkerFixtures.servingWorkerIn(home.runtime().openWindowOf(maven.pid(), ProcessHandle.current().pid()), home,
                WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        asksAQuestionAndEndsItsTurn(home, lamp);
        isAnsweredWithoutANewStatus(home, lamp);

        stopsItsTurn(home, lamp);

        assertThat(workingOnHarness(home)).containsExactly(lamp.id());
        stop(maven);
    }

    @Test
    void doesNotCountAWorkerWhoseQuestionIsNewerThanItsLastPromptEvenWithItsPipelineRunning() throws IOException {
        TestHome home = harnessLimitedTo(1);
        home.storing(tasks("fit-lamp"));
        Worker lamp = dispatch(home, "fit-lamp").launched().worker();
        becomesBusy(home, lamp);
        home.pipelineRuns().save(lamp.id().task(), new PipelineRun("01RUN", "running", "review", false, "abc123", null, false), TestHome.START);
        home.clock().advance(Duration.ofMinutes(5));

        home.recordStatus().report(lamp.id(), StatusState.BLOCKED, new StatusKey("nm-01run-review"), null, "The review asks about the wiring.");

        assertThat(workingOnHarness(home)).isEmpty();
        stopsItsTurn(home, lamp);
        assertThat(workingOnHarness(home)).isEmpty();
    }

    @Test
    void defersADispatchBehindBusyWorkersNamesThemAndStartsItWhenOneFinishes() throws IOException {
        TestHome home = harnessLimitedTo(2);
        home.storing(tasks("fit-lamp", "fit-horn", "fit-bell"));
        Worker lamp = dispatch(home, "fit-lamp").launched().worker();
        Worker horn = dispatch(home, "fit-horn").launched().worker();
        becomesBusy(home, lamp);
        becomesBusy(home, horn);

        DispatchOutcome bell = dispatch(home, "fit-bell");

        assertThat(bell.deferred().working()).containsExactly(lamp.id(), horn.id());
        assertThat(home.launchDeferredDispatches().launchWhereThereIsRoom()).isEmpty();

        stopsItsTurn(home, lamp);
        List<DispatchOutcome.Launched> launched = home.launchDeferredDispatches().launchWhereThereIsRoom();

        assertThat(launched).extracting(started -> started.worker().id().task()).containsExactly(new TaskId("fit-bell"));
    }

    @Test
    void countsAnIdleWorkerWhoseValidationPipelineIsRunning() throws IOException {
        TestHome home = harnessLimitedTo(1);
        home.storing(tasks("fit-lamp"));
        Worker lamp = dispatch(home, "fit-lamp").launched().worker();
        becomesBusy(home, lamp);
        stopsItsTurn(home, lamp);

        home.pipelineRuns().save(lamp.id().task(), new PipelineRun("01RUN", "running", "review", false, "abc123", null, false), TestHome.START);
        assertThat(workingOnHarness(home)).containsExactly(lamp.id());

        home.pipelineRuns().save(lamp.id().task(), new PipelineRun("01RUN", "completed", null, false, "abc123", null, false), TestHome.START);
        assertThat(workingOnHarness(home)).isEmpty();
    }

    @Test
    void countsAnIdleWorkerOnABuildOrTestRun() throws IOException {
        TestHome home = harnessLimitedTo(1);
        Process maven = processes.register(new ProcessBuilder("sh", "-c", "sleep 30; :", MAVEN_LAUNCHER, "-B", "verify").start());
        Worker lamp = WorkerFixtures.servingWorkerIn(home.runtime().openWindowOf(maven.pid(), ProcessHandle.current().pid()), home,
                WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        becomesBusy(home, lamp);
        stopsItsTurn(home, lamp);

        assertThat(workingOnHarness(home)).containsExactly(lamp.id());

        stop(maven);
        assertThat(workingOnHarness(home)).isEmpty();
    }

    private TestHome harnessLimitedTo(int capacity) throws IOException {
        return WorkerFixtures.homeWithProjects(directory).registering("""
                projects:
                  - name: harness
                    tracker: { kind: edi, projectKey: HRN }
                    forge: { kind: github, path: example-owner/harness, account: example-owner }
                    base: main
                    capacity: %d
                """.formatted(capacity));
    }

    private static List<Task> tasks(String... ids) {
        return IntStream.range(0, ids.length)
                .mapToObj(index -> WorkerFixtures.queuedShip(ids[index], "harness", "HRN-" + (index + 3)))
                .toList();
    }

    private static DispatchOutcome dispatch(TestHome home, String task) {
        return home.dispatchTask().dispatch(TaskReference.parse(task), new DispatchRequest(CategoryId.IMPLEMENTATION, "", List.of(), null),
                CallingSession.UNNAMED, Actor.PRIMARY);
    }

    private static void becomesBusy(TestHome home, Worker worker) {
        home.workerHooks().promptSubmitted(worker.id(), "Work on " + worker.id().task() + ".", null);
    }

    private static void stopsItsTurn(TestHome home, Worker worker) {
        home.workerHooks().stopping(worker.id());
    }

    private static List<WorkerId> workingOnHarness(TestHome home) {
        return home.workerPlaces().roomOn(home.registry().entryOf(HARNESS)).working();
    }

    private static void asksAQuestionAndEndsItsTurn(TestHome home, Worker worker) {
        becomesBusy(home, worker);
        home.recordStatus().report(worker.id(), StatusState.NEEDS_DECISION, new StatusKey("lamp-choice"), null, "Which lamp?");
        stopsItsTurn(home, worker);
    }

    private static void isAnsweredWithoutANewStatus(TestHome home, Worker worker) {
        home.clock().advance(Duration.ofMinutes(5));
        home.workerHooks().promptSubmitted(worker.id(), "Take the brass lamp.", null);
    }

    private static void stop(Process pane) {
        ProcessHandle root = pane.toHandle();
        List<ProcessHandle> tree = Stream.concat(root.descendants(), Stream.of(root)).toList();
        tree.forEach(ProcessHandle::destroyForcibly);
        tree.forEach(TestProcesses::awaitExit);
    }
}
