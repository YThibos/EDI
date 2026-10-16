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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.runtime.PrivateTmuxServer;
import io.github.ythibos.edi.adapter.runtime.TmuxClaudeRuntime;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.application.worker.SteerDelivery;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Keystroke;
import io.github.ythibos.edi.domain.worker.PaneState;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerWindow;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TestProcesses;
import io.github.ythibos.edi.testing.WorkerFixtures;

@DisplayName("4.5 Supervision and recovery")
class SupervisionAndRecoveryTest {

    @TempDir
    private Path directory;

    @Nested
    @DisplayName("I-23 A restart is a non-event")
    class ARestartIsANonEvent {

        @Test
        @DisplayName("AT-23 after killing the core and the primary mid-task, the session-start digest equals the pre-crash snapshot")
        void theDigestSurvivesARestart() {
            Pending.until(Slice.S4);
        }
    }

    @Nested
    @DisplayName("I-24 An outage is a numbered list")
    class AnOutageIsANumberedList {

        @Test
        @DisplayName("AT-24 three dead workers give three holds in a stable numbered list and '2 resume' relaunches only task 2")
        void deadWorkersBecomeANumberedHoldList() {
            Pending.until(Slice.S4);
        }
    }

    @Nested
    @DisplayName("I-25 Relaunch reuses the task's own worktree and session")
    class RelaunchReusesWorktreeAndSession {

        @Test
        @DisplayName("AT-25 a relaunch after a lost server lands in the recorded path with the recorded session id")
        void aRelaunchResumesTheRecordedSession() {
            Pending.until(Slice.S4);
        }
    }

    @Nested
    @DisplayName("I-26 Judge workers by structured state")
    class JudgeWorkersByStructuredState {

        @Test
        @DisplayName("AT-26 a worker whose last status is working but whose tmux server identity changed is reported dead")
        void aChangedServerIdentityMeansDead() {
            Pending.until(Slice.S4);
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
        void followUpWaitsForTheVerifiedRefresh() {
            Pending.until(Slice.S5);
        }
    }
}
