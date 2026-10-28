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

class WorkerIdentityTest {

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
    void refusesAWorkerAShellThatDropsOrRewritesWhatSaysWhoItIs() {
        for (String command : List.of(
                "unset EDI_TOKEN_FILE; edi dispatch fit-horn --category implementation",
                "env -u EDI_TOKEN_FILE edi dispatch fit-horn --category implementation",
                "env -i PATH=\"$PATH\" edi dispatch fit-horn --category implementation",
                "export EDI_TOKEN_FILE=/dev/null && edi dispatch fit-horn --category implementation",
                "EDI_TOKEN_FILE= edi dispatch fit-horn --category implementation",
                "EDI_TOKEN_FILE=; edi dispatch fit-horn --category implementation",
                "unset EDI_WORKER EDI_ROLE EDI_TASK_ID")) {
            HookAnswer answer = shell(command);
            assertThat(answer.verdict()).as(command).isEqualTo(HookAnswer.Verdict.DENY_THE_TOOL);
            assertThat(answer.reason()).as(command).contains("a worker calls the core only as itself");
        }
    }

    @Test
    void letsAWorkerReadThoseVariablesAndSetItsOwn() {
        for (String command : List.of("echo \"$EDI_TOKEN_FILE\"", "env | grep EDI_", "export PATH=\"$PATH:$PWD/bin\"", "LANG=C mvn -q test",
                "env -u MAVEN_OPTS mvn -q test", "unset MAVEN_OPTS")) {
            assertThat(shell(command)).as(command).isEqualTo(HookAnswer.PROCEED);
        }
    }

    @Test
    void leavesThePrimaryItsOwnEnvironment() {
        assertThat(home.guardToolCall().checkForPrimary(new ToolCall("Bash", "env -i bash -lc 'edi health'", null, home.home().root()),
                CallingSession.UNNAMED)).isEqualTo(HookAnswer.PROCEED);
    }

    private HookAnswer shell(String command) {
        return home.guardToolCall().checkForWorker(worker.id(), new ToolCall("Bash", command, null, worktree));
    }
}
