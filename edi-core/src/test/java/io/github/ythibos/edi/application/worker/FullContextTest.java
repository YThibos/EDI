package io.github.ythibos.edi.application.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.port.WorkerRuntime.Launch;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerHealth;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class FullContextTest {

    private static final TaskReference LAMP = new TaskReference.ById(new TaskId("fit-lamp"));

    @TempDir
    private Path directory;

    @Test
    void raisesABusyWorkerWhoseTranscriptEndsOnAFullContextOnce() throws IOException {
        TestHome home = homeWithAHarness();
        Worker lamp = busyWorker(home);
        Instant full = home.clock().now().plus(Duration.ofHours(1));
        home.clock().advance(Duration.ofHours(2));

        home.runtime().transcriptEndsOnAFullContextAt(full);
        assertThat(home.noticeFullContexts().notice()).extracting(Worker::id).containsExactly(lamp.id());
        assertThat(home.noticeFullContexts().notice()).isEmpty();
        home.judgeWorkers().judge();

        assertThat(healthOf(home, lamp)).isEqualTo(new WorkerHealth.ContextFull(full));
        assertThat(home.eventTypesOf(lamp.id().task())).containsOnlyOnce("ContextLimitReached").doesNotContain("WorkerBusyTooLong", "WorkerStale");
        assertThat(home.wakes().unacknowledged()).extracting(queued -> queued.wake().kind()).containsExactly(WakeKind.CONTEXT_FULL);
    }

    @Test
    void leavesAWorkerWhoseTranscriptEndsElsewhereAtWork() throws IOException {
        TestHome home = homeWithAHarness();
        Worker lamp = busyWorker(home);

        assertThat(home.noticeFullContexts().notice()).isEmpty();

        assertThat(healthOf(home, lamp)).isInstanceOf(WorkerHealth.Busy.class);
        assertThat(home.eventTypesOf(lamp.id().task())).doesNotContain("ContextLimitReached");
    }

    @Test
    void readsTheTranscriptFromThePromptWhenTheSessionStartWasMissed() throws IOException {
        TestHome home = homeWithAHarness();
        Worker lamp = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);

        home.workerHooks().promptSubmitted(lamp.id(), "Fit the lamp.", "/transcripts/lamp.jsonl");

        assertThat(home.workers().get(lamp.id()).orElseThrow().transcript()).isEqualTo("/transcripts/lamp.jsonl");
    }

    @Test
    void ignoresAFullContextWrittenBeforeTheWorkerWasLaunched() throws IOException {
        TestHome home = homeWithAHarness();
        Worker lamp = busyWorker(home);

        home.runtime().transcriptEndsOnAFullContextAt(lamp.launchedAt().minusSeconds(1));

        assertThat(home.noticeFullContexts().notice()).isEmpty();
    }

    @Test
    void aPromptThatGetsThroughLiftsTheFullContext() throws IOException {
        TestHome home = homeWithAHarness();
        Worker lamp = fullWorker(home);

        home.runtime().transcriptEndsOnAFullContextAt(null);
        home.workerHooks().promptSubmitted(lamp.id(), "/compact", null);
        home.workerHooks().stopping(lamp.id());

        assertThat(healthOf(home, lamp)).isInstanceOf(WorkerHealth.Idle.class);
    }

    @Test
    void keepsAMessageForTheFreshSessionInsteadOfTypingIntoTheFullContext() throws IOException {
        TestHome home = homeWithAHarness();
        fullWorker(home);

        SteerWorker.Steered steered = home.steerWorker().steer(LAMP, "Use the brass lamp.", null, Actor.PRIMARY);

        assertThat(steered.delivery()).isEqualTo(SteerDelivery.WAITING_FOR_A_FRESH_SESSION);
        assertThat(home.runtime().typedLines()).isEmpty();
    }

    @Test
    void refusesToRelaunchAFullContextWhoseSessionStillRuns() throws IOException {
        TestHome home = homeWithAHarness();
        fullWorker(home);

        assertThatThrownBy(() -> home.relaunchWorker().relaunch(LAMP, Actor.PRIMARY))
                .isInstanceOf(Refusal.class)
                .hasMessage("fit-lamp#1's context is full and its session still runs; stop it first with edi worker stop, then relaunch it for a fresh"
                        + " session with a handover.");
    }

    @Test
    @Tag("adapter")
    void relaunchesAStoppedWorkerWhoseContextFilledUpAfreshWithAHandover() throws IOException {
        TestHome home = WorkerFixtures.homeWithProjects(directory);
        Worker lamp = WorkerFixtures.dispatchedShip(home, "fit-lamp", "HRN-3");
        Path copy = home.runtime().lastLaunch().worktree();
        home.workerHooks().sessionStarted(lamp.id(), lamp.session(), "/transcripts/lamp.jsonl");
        home.workerHooks().promptSubmitted(lamp.id(), "Fit the lamp.", null);
        home.recordStatus().report(lamp.id(), StatusState.WORKING, null, null, "commit 1 of 2 done; next: the brass horn");
        FixtureRepository.at(copy).write("lamp.txt", "brass\n").commit("HRN-3 Fit the lamp").write("horn.txt", "half a horn\n");
        home.pipelineRuns().save(lamp.id().task(), new PipelineRun("01RUN", "awaiting_approval", "review", null, null), home.clock().now());
        home.runtime().transcriptEndsOnAFullContextAt(home.clock().now());
        home.noticeFullContexts().notice();
        home.workers().update(home.workers().get(lamp.id()).orElseThrow().stopRequested(home.clock().now()));
        home.runtime().closeEveryWindow();

        home.relaunchWorker().relaunch(LAMP, Actor.PRIMARY);

        Launch fresh = home.runtime().lastLaunch();
        Path taskDirectory = home.home().taskDirectory(lamp.id().task());
        assertThat(fresh.kind()).isEqualTo(LaunchKind.FRESH);
        assertThat(fresh.session()).isNotEqualTo(lamp.session());
        assertThat(fresh.worktree()).isEqualTo(copy);
        assertThat(fresh.firstPrompt()).isEqualTo("⁣EDI_OP: fresh start after its context filled up: read the handover "
                + taskDirectory.resolve("handover.md") + " and carry on from where it leaves off");
        assertThat(Files.readString(taskDirectory.resolve("handover.md"))).contains(
                "# Handover of fit-lamp",
                "fit-lamp#2 takes over from fit-lamp#1 in the same isolated copy, because its context filled up.",
                "Your copy " + copy + " is on feature/HRN-3 at ",
                "Commits beyond origin/main, oldest first:\n\n- ",
                " HRN-3 Fit the lamp\n",
                "Uncommitted changes:\n\n- horn.txt\n",
                "The latest pipeline run, 01RUN, is awaiting_approval, at its review step.",
                "The brief: " + taskDirectory.resolve("brief.md") + ".",
                "Its last status, in its own words:\n\n```text\nworking: commit 1 of 2 done; next: the brass horn\n```\n",
                "Answered since the task started: nothing.",
                "Run `edi inbox` first, then read the brief.");
        assertThat(taskDirectory.resolve("brief.md")).content().contains("fit-lamp#2");
        assertThat(home.workers().get(lamp.id()).orElseThrow().isRetired()).isTrue();
    }

    @Test
    @Tag("adapter")
    void refusesToResumeASessionWhoseContextIsFull() throws IOException {
        TestHome home = WorkerFixtures.homeWithProjects(directory);
        Worker lamp = WorkerFixtures.dispatchedShip(home, "fit-lamp", "HRN-3");
        home.workerHooks().promptSubmitted(lamp.id(), "Fit the lamp.", null);
        home.runtime().transcriptEndsOnAFullContextAt(home.clock().now());
        home.noticeFullContexts().notice();
        home.runtime().closeEveryWindow();

        assertThatThrownBy(() -> home.launchWorker().relaunch(lamp.id().task(), LaunchKind.RESUME, null, null, "its window closed", Actor.PRIMARY))
                .isInstanceOf(Refusal.class)
                .hasMessage("fit-lamp#1's context is full, so a resumed session would stop on it again; start it fresh instead.");
    }

    private TestHome homeWithAHarness() throws IOException {
        return TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
    }

    private static Worker busyWorker(TestHome home) {
        Worker worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        home.workerHooks().sessionStarted(worker.id(), worker.session(), "/transcripts/lamp.jsonl");
        home.workerHooks().promptSubmitted(worker.id(), "Fit the lamp.", null);
        return worker;
    }

    private static Worker fullWorker(TestHome home) {
        Worker worker = busyWorker(home);
        home.runtime().transcriptEndsOnAFullContextAt(home.clock().now());
        home.noticeFullContexts().notice();
        return worker;
    }

    private static WorkerHealth healthOf(TestHome home, Worker worker) {
        return home.workerList().judged(home.workers().get(worker.id()).orElseThrow()).health();
    }
}
