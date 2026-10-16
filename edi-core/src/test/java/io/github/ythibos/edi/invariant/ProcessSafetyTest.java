package io.github.ythibos.edi.invariant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.worker.HookAnswer;
import io.github.ythibos.edi.application.worker.ToolCall;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@DisplayName("4.6 Process safety")
class ProcessSafetyTest {

    @TempDir
    private Path directory;

    private TestHome home;
    private Worker worker;

    private void prepareAWorker() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
    }

    private HookAnswer forTheWorker(String command) {
        return home.guardToolCall().checkForWorker(worker.id(), new ToolCall("Bash", command, null, home.home().worktreeOf(
                new ProjectName("harness"), new TaskId("fit-lamp"))));
    }

    private HookAnswer forThePrimary(String command) {
        return home.guardToolCall().checkForPrimary(new ToolCall("Bash", command, null, home.home().root()));
    }

    private void assertDenied(HookAnswer answer, String reason) {
        assertThat(answer.verdict()).isEqualTo(HookAnswer.Verdict.DENY_THE_TOOL);
        assertThat(answer.reason()).startsWith("EDI's guard refuses this: ").contains(reason);
    }

    @Nested
    @DisplayName("I-30 No broad process signals")
    class NoBroadProcessSignals {

        @Test
        @DisplayName("AT-30 the pre-tool guard refuses pkill, killall and kill $(pgrep ...) in worker and primary Bash calls")
        void theGuardRefusesPatternKills() throws IOException {
            prepareAWorker();

            for (String command : List.of("pkill -f surefire", "killall java", "kill $(pgrep -f surefire)", "kill -9 `pgrep java`",
                    "pgrep -f mvn | xargs kill", "sudo pkill node", "bash -c 'pkill -f vite'")) {
                assertDenied(forTheWorker(command), "kill only a process id you started and captured (I-30)");
                assertDenied(forThePrimary(command), "kill only a process id you started and captured (I-30)");
            }
            assertThat(forTheWorker("kill \"$build_pid\"").verdict()).isEqualTo(HookAnswer.Verdict.PROCEED);
            assertThat(forThePrimary("pgrep -f surefire").verdict()).isEqualTo(HookAnswer.Verdict.PROCEED);
            assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).filteredOn("ToolCallRefused"::equals).hasSize(7);
        }
    }

    @Nested
    @DisplayName("I-31 Workers live on EDI's private tmux socket, and nobody runs bare-socket tmux")
    class WorkersLiveOnThePrivateSocket {

        @Test
        @DisplayName("AT-31 the guard refuses tmux kill-server, tmux kill-session without a socket, and tmux -L edi kill-server from a worker")
        void theGuardRefusesBareSocketTmux() throws IOException {
            prepareAWorker();

            for (String command : List.of("tmux kill-server", "tmux kill-session -t workers", "tmux ls")) {
                assertDenied(forTheWorker(command), "name the socket with -L or -S (I-31)");
                assertDenied(forThePrimary(command), "name the socket with -L or -S (I-31)");
            }
            for (String command : List.of("tmux -L edi kill-server", "tmux -L edi kill-window -t @2", "tmux -L edi send-keys -t @2 Enter")) {
                assertDenied(forTheWorker(command), "belong to EDI's core");
                assertDenied(forThePrimary(command), "belong to EDI's core");
            }
            assertThat(forTheWorker("tmux -L edi-test-42 kill-server").verdict()).isEqualTo(HookAnswer.Verdict.PROCEED);
            assertThat(forTheWorker("tmux -L edi list-windows").verdict()).isEqualTo(HookAnswer.Verdict.PROCEED);
        }
    }

    @Nested
    @DisplayName("I-32 Secrets are read at call time and never printed")
    class SecretsAreNeverPrinted {

        @Test
        @DisplayName("AT-32 a canary secret in a fixture keys file never appears in logs, events or stdout after a full adapter run")
        void aCanarySecretNeverSurfaces() {
            Pending.until(Slice.S6);
        }
    }
}
