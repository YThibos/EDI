package io.github.ythibos.edi.invariant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.runtime.PrivateTmuxServer;
import io.github.ythibos.edi.adapter.runtime.TmuxClaudeRuntime;
import io.github.ythibos.edi.application.delivery.FollowMerges.MergedWork;
import io.github.ythibos.edi.application.dispatch.DispatchRequest;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.application.port.WorkerRuntime.Launch;
import io.github.ythibos.edi.application.supervision.OutageList;
import io.github.ythibos.edi.application.worker.SteerDelivery;
import io.github.ythibos.edi.application.worker.WorkerList;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.primary.Standing;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.recovery.RecoveryAction;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.domain.worker.DeathCause;
import io.github.ythibos.edi.domain.worker.Keystroke;
import io.github.ythibos.edi.domain.worker.PaneState;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerHealth;
import io.github.ythibos.edi.domain.worker.WorkerWindow;
import io.github.ythibos.edi.testing.Cards;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TestProcesses;
import io.github.ythibos.edi.testing.WorkerFixtures;
import io.github.ythibos.edi.web.primary.SessionStartDigestText;
import io.github.ythibos.edi.web.worker.WorkerListText;

@DisplayName("4.5 Supervision and recovery")
class SupervisionAndRecoveryTest {

    @TempDir
    private Path directory;

    @Nested
    @DisplayName("I-23 A restart is a non-event")
    class ARestartIsANonEvent {

        @RegisterExtension
        private final TestProcesses processes = new TestProcesses();

        @Test
        @DisplayName("AT-23 after killing the core and the primary mid-task, the session-start digest equals the pre-crash snapshot")
        @Tag("adapter")
        void theDigestSurvivesARestart() throws Exception {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            Worker lamp = WorkerFixtures.dispatchedShip(home, "fit-lamp", "HRN-3");
            home.workerHooks().sessionStarted(lamp.id(), lamp.session(), null);
            home.recordStatus().report(lamp.id(), StatusState.WORKING, null, null, "Fitting the brass lamp.");
            home.storing(List.of(WorkerFixtures.queued("choose-lamp", TaskKind.CAPTAIN, "harness", null), WorkerFixtures.queuedShip("tune-horn", "harness", "HRN-4")));
            home.raiseDecision().raise(new TaskReference.ById(new TaskId("choose-lamp")), Cards.card("Which lamp?"), Actor.PRIMARY);
            Process killedPrimary = processes.register(new ProcessBuilder("sleep", "30").start());
            UUID beforeTheCrash = UUID.randomUUID();
            home.primaryHooks().sessionStarted(beforeTheCrash, killedPrimary.pid(), true);
            String preCrashDigest = digestOf(home, beforeTheCrash);

            killedPrimary.destroyForcibly();
            assertThat(TestProcesses.awaitExit(killedPrimary.toHandle())).isTrue();
            TestHome restarted = home.restarted();
            UUID afterTheRestart = UUID.randomUUID();
            restarted.primaryHooks().sessionStarted(afterTheRestart, null, true);

            assertThat(restarted.primarySessions().standingOf(afterTheRestart)).isEqualTo(Standing.PRIMARY);
            assertThat(digestOf(restarted, afterTheRestart)).isEqualTo(preCrashDigest)
                    .contains("This session is the primary", "D1  choose-lamp  Which lamp?", "fit-lamp#1  idle since", "working: Fitting the brass lamp.",
                            "Queued: 1.");
        }

        private static String digestOf(TestHome home, UUID session) {
            return new SessionStartDigestText(new WorkerListText()).text(home.sessionStartDigest().digestFor(session));
        }
    }

    @Nested
    @DisplayName("I-24 An outage is a numbered list")
    class AnOutageIsANumberedList {

        @Test
        @DisplayName("AT-24 three dead workers give three holds in a stable numbered list and '2 resume' relaunches only task 2")
        @Tag("adapter")
        void deadWorkersBecomeANumberedHoldList() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            WorkerFixtures.dispatchedShip(home, "fit-lamp", "HRN-3");
            Worker horn = WorkerFixtures.dispatchedShip(home, "tune-horn", "HRN-4");
            WorkerFixtures.dispatchedShip(home, "ring-bell", "HRN-5");
            int launchesBeforeTheOutage = home.runtime().launches().size();

            home.runtime().restartTheServer();
            home.judgeWorkers().judge();
            OutageList listed = home.outageList().list();
            home.judgeWorkers().judge();

            assertThat(home.runtime().launches()).hasSize(launchesBeforeTheOutage);
            assertThat(numbersAndTasks(listed)).containsExactly("1 fit-lamp", "2 tune-horn", "3 ring-bell");
            assertThat(numbersAndTasks(home.outageList().list())).isEqualTo(numbersAndTasks(listed));
            assertThat(listed.entries()).allSatisfy(entry -> {
                assertThat(entry.task().isHeld()).isTrue();
                assertThat(entry.worker().route()).hasToString("claude-sonnet-5-5 at high");
                assertThat(entry.routeNow()).hasToString("claude-sonnet-5-5 at high");
                assertThat(entry.entry().cause()).isEqualTo(DeathCause.SERVER_CHANGED);
            });

            home.recoverFromOutage().answer(2, RecoveryAction.RESUME, null, Actor.PRIMARY);

            Launch resumed = home.runtime().lastLaunch();
            assertThat(home.runtime().launches()).hasSize(launchesBeforeTheOutage + 1);
            assertThat(resumed.worker()).isEqualTo(horn.id().next());
            assertThat(resumed.resumesTheSession()).isTrue();
            assertThat(resumed.session()).isEqualTo(horn.session());
            assertThat(numbersAndTasks(home.outageList().list())).containsExactly("1 fit-lamp", "3 ring-bell");
            assertThat(home.tasks().get(new TaskId("tune-horn")).isHeld()).isFalse();
            assertThat(home.tasks().get(new TaskId("fit-lamp")).isHeld()).isTrue();
            assertThat(home.tasks().get(new TaskId("ring-bell")).isHeld()).isTrue();
        }

        @Test
        @DisplayName("A simulated machine restart puts every worker of the old boot on the numbered list")
        @Tag("adapter")
        void aMachineRestartProducesTheNumberedList() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            for (Worker worker : List.of(WorkerFixtures.dispatchedShip(home, "fit-lamp", "HRN-3"), WorkerFixtures.dispatchedShip(home, "tune-horn", "HRN-4"))) {
                home.workerHooks().sessionStarted(worker.id(), worker.session(), null);
                home.recordStatus().report(worker.id(), StatusState.WORKING, null, null, "Working on " + worker.id().task() + ".");
            }

            home.restartTheMachine();
            TestHome afterTheRestart = home.restarted();
            afterTheRestart.judgeWorkers().judge();
            OutageList listed = afterTheRestart.outageList().list();

            assertThat(numbersAndTasks(listed)).containsExactly("1 fit-lamp", "2 tune-horn");
            assertThat(listed.entries()).extracting(entry -> entry.entry().cause()).containsOnly(DeathCause.MACHINE_RESTARTED);
            assertThat(afterTheRestart.runtime().launches()).hasSize(2);
            assertThat(afterTheRestart.eventTypesOf(new TaskId("fit-lamp"))).contains("TaskHeld", "LostInOutage").doesNotContain("WorkerDied");
            assertThat(afterTheRestart.wakes().unacknowledged()).extracting(queued -> queued.wake().kind()).containsOnly(WakeKind.LOST_IN_OUTAGE);
        }

        @Test
        @DisplayName("A machine restart lists only the workers that were still working, not one whose done was accepted")
        @Tag("adapter")
        void aMachineRestartLeavesFinishedWorkOffTheList() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            Worker lamp = WorkerFixtures.dispatchedShip(home, "fit-lamp", "HRN-3");
            for (Worker worker : List.of(lamp, WorkerFixtures.dispatchedShip(home, "tune-horn", "HRN-4"), WorkerFixtures.dispatchedShip(home, "ring-bell", "HRN-5"))) {
                home.workerHooks().sessionStarted(worker.id(), worker.session(), null);
                home.recordStatus().report(worker.id(), StatusState.WORKING, null, null, "Working on " + worker.id().task() + ".");
            }
            WorkerFixtures.opensTheDraftOf(home, lamp);
            home.recordStatus().report(lamp.id(), StatusState.DONE, null, null, "The lamp is fitted.");

            home.restartTheMachine();
            TestHome afterTheRestart = home.restarted();
            afterTheRestart.judgeWorkers().judge();

            assertThat(numbersAndTasks(afterTheRestart.outageList().list())).containsExactly("1 tune-horn", "2 ring-bell");
            assertThat(afterTheRestart.workerList().judged(afterTheRestart.workers().get(lamp.id()).orElseThrow()).health())
                    .isEqualTo(new WorkerHealth.Finished());
            assertThat(afterTheRestart.tasks().get(new TaskId("fit-lamp")).isHeld()).isFalse();
            assertThat(afterTheRestart.eventTypesOf(new TaskId("fit-lamp"))).doesNotContain("LostInOutage", "TaskHeld", "WorkerDied");
        }

        private static List<String> numbersAndTasks(OutageList list) {
            return list.entries().stream().map(entry -> entry.entry().number() + " " + entry.task().id()).toList();
        }
    }

    @Nested
    @DisplayName("I-25 Relaunch reuses the task's own worktree and session")
    class RelaunchReusesWorktreeAndSession {

        @Test
        @DisplayName("AT-25 a relaunch after a lost server lands in the recorded path with the recorded session id")
        @Tag("adapter")
        void aRelaunchResumesTheRecordedSession() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            Worker lamp = WorkerFixtures.dispatchedShip(home, "fit-lamp", "HRN-3");
            Path recordedCopy = home.runtime().lastLaunch().worktree();
            TaskReference task = new TaskReference.ById(new TaskId("fit-lamp"));

            assertThatThrownBy(() -> home.relaunchWorker().relaunch(task, Actor.PRIMARY)).isInstanceOf(Refusal.class).hasMessageContaining("still running");
            home.runtime().loseTheServer();
            home.relaunchWorker().relaunch(task, Actor.PRIMARY);

            Launch relaunch = home.runtime().lastLaunch();
            assertThat(relaunch.worktree()).isEqualTo(recordedCopy).isEqualTo(home.home().root().resolve("worktrees/harness/fit-lamp"));
            assertThat(relaunch.session()).isEqualTo(lamp.session());
            assertThat(relaunch.resumesTheSession()).isTrue();
            assertThat(relaunch.firstPrompt()).startsWith("\u2063EDI_OP: resumed after its tmux server is gone: your copy is on feature/HRN-3 at ");
            assertThat(home.workers().get(lamp.id()).orElseThrow().isRetired()).isTrue();
            assertThat(home.workers().latestOf(new TaskId("fit-lamp")).orElseThrow().session()).isEqualTo(lamp.session());
        }
    }

    @Nested
    @DisplayName("I-26 Judge workers by structured state")
    class JudgeWorkersByStructuredState {

        @Test
        @DisplayName("AT-26 a worker whose last status is working but whose tmux server identity changed is reported dead")
        void aChangedServerIdentityMeansDead() throws IOException {
            TestHome home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
            Worker worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
            home.workerHooks().sessionStarted(worker.id(), worker.session(), null);
            home.workerHooks().promptSubmitted(worker.id(), "Fit the lamp.");
            home.recordStatus().report(worker.id(), StatusState.WORKING, null, null, "Fitting the lamp.");

            home.runtime().restartTheServer();
            WorkerList serving = home.workerList().serving();
            home.judgeWorkers().judge();

            assertThat(serving.entries().getFirst().health()).isEqualTo(new WorkerHealth.Dead(DeathCause.SERVER_CHANGED));
            assertThat(new WorkerListText().text(serving)).contains("fit-lamp#1  dead: its tmux server changed", "working: Fitting the lamp.")
                    .doesNotContain("busy");
            assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).containsOnlyOnce("WorkerDied");
        }
    }

    @Nested
    @DisplayName("I-27 A steer is verified as landed")
    class ASteerIsVerified {

        @Test
        @DisplayName("AT-27 an unacknowledged message raises a 'steer not received' event after the timeout")
        void anUnacknowledgedSteerRaisesAnEvent() throws IOException {
            TestHome home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
            Worker worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
            home.workerHooks().stopping(worker.id());
            home.runtime().showing(PaneState.DIALOG_PERMISSION);

            assertThat(home.steerWorker().steer(TaskReference.parse("fit-lamp"), "Use the brass lamp.", null, Actor.PRIMARY).delivery())
                    .isEqualTo(SteerDelivery.WAITING_FOR_THE_PANE);
            home.clock().advance(Duration.ofMinutes(5).minusSeconds(1));
            assertThat(home.noticeOverdueSteers().notice()).isEmpty();
            home.clock().advance(Duration.ofSeconds(1));
            assertThat(home.noticeOverdueSteers().notice()).hasSize(1);
            home.clock().advance(Duration.ofMinutes(30));
            assertThat(home.noticeOverdueSteers().notice()).isEmpty();
            assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).containsOnlyOnce("SteerNotReceived");

            home.steerWorker().steer(TaskReference.parse("fit-lamp"), "Then fit the horn.", null, Actor.PRIMARY);
            home.workerHooks().stopping(worker.id());
            home.clock().advance(Duration.ofMinutes(10));
            assertThat(home.noticeOverdueSteers().notice()).isEmpty();
            assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).containsOnlyOnce("SteerNotReceived").contains("SteerReceived");
        }
    }

    @Nested
    @DisplayName("I-28 Never press a key into a dialog blind")
    class NeverPressAKeyBlind {

        @RegisterExtension
        private final TestProcesses processes = new TestProcesses();

        @Test
        @DisplayName("AT-28 the tmux adapter sends only literal text plus Enter after a positive idle probe, or Escape")
        @Tag("adapter")
        void theTmuxAdapterRefusesOtherKeys() throws IOException {
            assumeTrue(PrivateTmuxServer.isInstalled(), "tmux is not installed");
            try (PrivateTmuxServer server = PrivateTmuxServer.started(processes)) {
                TmuxClaudeRuntime runtime = server.runtime(directory, "");
                Path typedIntoTheIdlePrompt = directory.resolve("typed-into-idle.txt");
                WorkerWindow idle = server.windowShowing("idle-fresh-session.txt", directory, typedIntoTheIdlePrompt);
                WorkerWindow dialog = server.windowShowing("trust-dialog.txt", directory, directory.resolve("typed-into-dialog.txt"));
                List<String> dialogBefore = server.visibleLines(dialog);

                runtime.press(idle, Keystroke.line("run edi inbox"));
                runtime.press(idle, Keystroke.line("Enter"));
                assertThatThrownBy(() -> runtime.press(dialog, Keystroke.line("Yes, I trust this folder")))
                        .isInstanceOf(Refusal.class).hasMessageContaining("shows dialog-trust, not the idle prompt");
                assertThat(PrivateTmuxServer.awaitContent(typedIntoTheIdlePrompt, "Enter")).isEqualTo("run edi inbox\nEnter\n");
                assertThat(server.visibleLines(dialog)).isEqualTo(dialogBefore);

                runtime.press(dialog, Keystroke.escape());
                assertThat(server.awaitLines(dialog, lines -> lines.stream().anyMatch(line -> line.contains("^[")))).anyMatch(line -> line.contains("^["));
            }
            assertThat(Keystroke.class.getPermittedSubclasses()).containsExactlyInAnyOrder(Keystroke.Line.class, Keystroke.Escape.class);
            for (String key : List.of("Enter\n", "line\r", "\t", "\u001b[A", "\u0003")) {
                assertThatIllegalArgumentException().isThrownBy(() -> Keystroke.line(key)).withMessageContaining("line");
            }
            assertThat(Arrays.stream(WorkerRuntime.class.getMethods())
                    .filter(method -> Arrays.asList(method.getParameterTypes()).contains(Keystroke.class))
                    .map(Method::getName)).containsExactly("press");
        }
    }

    @Nested
    @DisplayName("I-29 A captain-side merge refreshes the clone before follow-up work")
    class AMergeRefreshesTheClone {

        @Test
        @DisplayName("AT-29 follow-up work on a project with a pending refresh waits until the fetch verified the merge commit")
        @Tag("adapter")
        void followUpWaitsForTheVerifiedRefresh() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            FixtureRepository upstream = FixtureRepository.at(directory.resolve("upstream/harness"));
            Worker lamp = WorkerFixtures.dispatchedShip(home, "fit-lamp", "HRN-3");
            FixtureRepository copy = FixtureRepository.at(home.home().worktreeOf(new ProjectName("harness"), new TaskId("fit-lamp")));
            copy.write("lamp.txt", "brass\n").commit("HRN-3 Fit the lamp");
            MergeRequestView draft = WorkerFixtures.opensTheDraftOf(home, lamp);
            home.recordStatus().report(lamp.id(), StatusState.DONE, null, null, draft.address().url());
            String landed = squashedOnASideBranchOf(upstream, copy, "feature/HRN-3");
            home.forge().merges(draft.address(), landed);
            home.storing(List.of(WorkerFixtures.queuedShip("fit-horn", "harness", "HRN-4")));

            MergedWork merged = home.followMerges().mergedSaysTheCaptain(draft.address().url(), Actor.PRIMARY);

            assertThat(merged.cleanUp().done()).isFalse();
            assertThatThrownBy(() -> dispatch(home, "fit-horn"))
                    .isInstanceOf(Refusal.class)
                    .hasMessageContaining("Follow-up work on harness waits until its clone holds commit " + landed + " on origin/main");
            assertThat(home.runtime().launches()).hasSize(1);

            upstream.run("merge", "--quiet", "--ff-only", "incoming");
            dispatch(home, "fit-horn");

            assertThat(home.runtime().launches()).hasSize(2);
            assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).contains("DraftMerged", "CloneRefreshed");

            home.followMerges().finishEarlierMerges();

            assertThat(home.tasks().get(new TaskId("fit-lamp")).state()).isEqualTo(TaskState.DONE);
            assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).endsWith("TaskCostRecorded", "ReviewCounted", "TaskCleanedUp");
            assertThat(copy.root()).doesNotExist();
        }

        private static String squashedOnASideBranchOf(FixtureRepository upstream, FixtureRepository copy, String branch) throws IOException {
            upstream.run("switch", "--quiet", "--create", "incoming");
            upstream.run("fetch", "--quiet", copy.root().toString(), branch);
            upstream.run("merge", "--quiet", "--squash", "FETCH_HEAD");
            String landed = upstream.commit("Fit the lamp, squashed").head();
            upstream.run("switch", "--quiet", "main");
            return landed;
        }

        private static void dispatch(TestHome home, String task) {
            home.dispatchTask().dispatch(TaskReference.parse(task), new DispatchRequest(CategoryId.IMPLEMENTATION, "", List.of(), null), Actor.PRIMARY);
        }
    }
}
