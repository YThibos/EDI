package io.github.ythibos.edi.application.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class TmuxInputTest {

    @TempDir
    private Path directory;

    private TestHome home;
    private Worker worker;
    private Path worktree;

    @BeforeEach
    void serveAWorker() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        worktree = Files.createDirectories(home.home().worktreeOf(new ProjectName("harness"), new TaskId("fit-lamp")));
    }

    @Test
    void refusesAWorkerTypingIntoThePrimarySessionOnTheDefaultServer() {
        for (String command : List.of(
                "tmux -L default send-keys -t edi-261028-0930 'edi task drop fit-horn --discard-work' Enter",
                "tmux -L default set-buffer 'edi task drop fit-horn' \\; paste-buffer -t edi-261028-0930",
                "tmux -L default run-shell 'tmux send-keys -t edi-261028-0930 yes Enter'",
                "tmux -S /tmp/tmux-1000/default if-shell true 'send-keys -t edi-261028-0930 yes Enter'",
                "tmux -L default source-file ./keys.conf")) {
            HookAnswer answer = shell(command);
            assertThat(answer.verdict()).as(command).isEqualTo(HookAnswer.Verdict.DENY_THE_TOOL);
            assertThat(answer.reason()).as(command).contains("a worker never sends input to a tmux pane");
        }
    }

    @Test
    void refusesControlOfEdisServerThroughALinkToItsSocket() throws IOException {
        Path sockets = Files.createDirectories(directory.resolve("sockets"));
        Path ediSocket = Files.createFile(sockets.resolve(home.runtime().socket()));
        Path link = Files.createSymbolicLink(worktree.resolve("harbour"), ediSocket);

        HookAnswer answer = shell("tmux -S " + link + " kill-server");
        HookAnswer relative = shell("tmux -S harbour kill-window -t @3");

        assertThat(answer.verdict()).isEqualTo(HookAnswer.Verdict.DENY_THE_TOOL);
        assertThat(answer.reason()).contains("belong to the core");
        assertThat(relative.verdict()).isEqualTo(HookAnswer.Verdict.DENY_THE_TOOL);
        assertThat(home.guardToolCall().checkForPrimary(new ToolCall("Bash", "tmux -S " + link + " kill-server", null, worktree), CallingSession.UNNAMED)
                .verdict()).isEqualTo(HookAnswer.Verdict.DENY_THE_TOOL);
    }

    @Test
    void readsTheWorkingDirectoryInASocketPathAsTheShellWould() throws IOException {
        Path sockets = Files.createDirectories(directory.resolve("sockets"));
        Path ediSocket = Files.createFile(sockets.resolve(home.runtime().socket()));
        Files.createSymbolicLink(worktree.resolve("harbour"), ediSocket);

        for (String command : List.of("tmux -S \"$PWD/harbour\" kill-server", "tmux -S \"${PWD}/harbour\" kill-window -t @3")) {
            HookAnswer answer = shell(command);
            assertThat(answer.verdict()).as(command).isEqualTo(HookAnswer.Verdict.DENY_THE_TOOL);
            assertThat(answer.reason()).as(command).contains("belong to the core");
        }
    }

    @Test
    void letsAWorkerReadAnyServerAndControlItsOwnPrivateOne() {
        for (String command : List.of("tmux -L default list-sessions", "tmux -L edi capture-pane -p -t @3", "tmux -L edi-test-42 kill-server",
                "tmux -L edi-test-42 new-session -d -s probe")) {
            assertThat(shell(command)).as(command).isEqualTo(HookAnswer.PROCEED);
        }
    }

    private HookAnswer shell(String command) {
        return home.guardToolCall().checkForWorker(worker.id(), new ToolCall("Bash", command, null, worktree));
    }
}
