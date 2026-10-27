package io.github.ythibos.edi.application.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.wake.Wake;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.domain.worker.HeavyRun;
import io.github.ythibos.edi.domain.worker.HeavyRuns;
import io.github.ythibos.edi.domain.worker.HeavyRuns.WorkerRun;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TestProcesses;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class HeavyRunsAtOnceTest {

    private static final String MAVEN_LAUNCHER = "org.codehaus.plexus.classworlds.launcher.Launcher";

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    @Test
    void warnsOnceWhenASecondWorkerStartsAHeavyRunAndShowsTheRunsWhileTheyOverlap() throws IOException {
        TestHome home = homeWithAHarness();
        Worker lamp = workerRunning(home, "fit-lamp", "HRN-3", "-Pnative,quick-build", "verify");
        home.noticeHeavyRunsAtOnce().notice();

        assertThat(home.noticeHeavyRunsAtOnce().atOnce()).isEmpty();
        Worker horn = workerRunning(home, "tune-horn", "HRN-4", "-B", "verify");
        home.noticeHeavyRunsAtOnce().notice();
        home.noticeHeavyRunsAtOnce().notice();

        assertThat(home.noticeHeavyRunsAtOnce().atOnce()).contains(new HeavyRuns(List.of(
                new WorkerRun(lamp.id(), HeavyRun.NATIVE_BUILD), new WorkerRun(horn.id(), HeavyRun.TEST_RUN))));
        assertThat(home.eventTypesOf(lamp.id().task())).doesNotContain("HeavyRunsAtOnce");
        assertThat(home.eventTypesOf(horn.id().task())).containsOnlyOnce("HeavyRunsAtOnce");
        assertThat(home.wakes().unacknowledged()).extracting(queued -> queued.wake())
                .extracting(Wake::kind, Wake::worker, Wake::detail)
                .containsExactly(tuple(WakeKind.HEAVY_RUNS_AT_ONCE, horn.id(), "a test run while fit-lamp#1 runs a native build"));
        stop(lamp);
        stop(horn);
    }

    @Test
    void stopsShowingTheRunsOnceOnlyOneIsLeft() throws IOException {
        TestHome home = homeWithAHarness();
        Worker lamp = workerRunning(home, "fit-lamp", "HRN-3", "-Pnative", "verify");
        Worker horn = workerRunning(home, "tune-horn", "HRN-4", "test");
        home.noticeHeavyRunsAtOnce().notice();

        stop(horn);
        home.noticeHeavyRunsAtOnce().notice();

        assertThat(home.noticeHeavyRunsAtOnce().atOnce()).isEmpty();
        stop(lamp);
    }

    @Test
    void leavesOutAWorkerWhosePaneIdNowBelongsToAnotherProcess() throws IOException {
        TestHome home = homeWithAHarness();
        Worker lamp = workerRunning(home, "fit-lamp", "HRN-3", "-Pnative", "verify");
        WorkerFixtures.servingWorkerIn(home.runtime().openWindowOf(lamp.window().panePid(), 1), home,
                WorkerFixtures.queuedShip("tune-horn", "harness", "HRN-4"), CategoryId.IMPLEMENTATION);
        home.noticeHeavyRunsAtOnce().notice();

        assertThat(home.noticeHeavyRunsAtOnce().atOnce()).isEmpty();
        assertThat(home.wakes().unacknowledged()).isEmpty();
        stop(lamp);
    }

    private TestHome homeWithAHarness() throws IOException {
        return TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
    }

    private Worker workerRunning(TestHome home, String task, String key, String... mavenArguments) throws IOException {
        List<String> pane = Stream.concat(Stream.of("sh", "-c", "sleep 30; :", MAVEN_LAUNCHER), Stream.of(mavenArguments)).toList();
        Process maven = processes.register(new ProcessBuilder(pane).start());
        return WorkerFixtures.servingWorkerIn(home.runtime().openWindowOf(maven.pid(), ProcessHandle.current().pid()), home,
                WorkerFixtures.queuedShip(task, "harness", key), CategoryId.IMPLEMENTATION);
    }

    private static void stop(Worker worker) {
        ProcessHandle pane = ProcessHandle.of(worker.window().panePid()).orElseThrow();
        List<ProcessHandle> tree = Stream.concat(pane.descendants(), Stream.of(pane)).toList();
        tree.forEach(ProcessHandle::destroyForcibly);
        tree.forEach(TestProcesses::awaitExit);
    }
}
