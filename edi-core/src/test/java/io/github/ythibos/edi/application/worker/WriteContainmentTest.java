package io.github.ythibos.edi.application.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class WriteContainmentTest {

    @TempDir
    private Path directory;

    @Test
    void letsAWorkerWriteOnlyInItsIsolatedCopyAndItsTaskDirectory() throws IOException {
        TestHome home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        Worker worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        Path worktree = Files.createDirectories(home.home().worktreeOf(new ProjectName("harness"), new TaskId("fit-lamp")));
        Path neighbour = Files.createDirectories(home.home().worktreeOf(new ProjectName("harness"), new TaskId("fit-horn")));
        Files.createSymbolicLink(worktree.resolve("escape"), neighbour);

        assertThat(write(home, worker, "src/Lamp.java", worktree)).isEqualTo(HookAnswer.PROCEED);
        assertThat(write(home, worker, home.home().taskDirectory(new TaskId("fit-lamp")).resolve("report.md").toString(), worktree))
                .isEqualTo(HookAnswer.PROCEED);
        for (String outside : List.of(neighbour.resolve("Horn.java").toString(), "../fit-horn/Horn.java", "escape/Horn.java",
                home.home().root().resolve("projects/harness/README.md").toString(), "/tmp/scratch.txt")) {
            HookAnswer answer = write(home, worker, outside, worktree);
            assertThat(answer.verdict()).as(outside).isEqualTo(HookAnswer.Verdict.DENY_THE_TOOL);
            assertThat(answer.reason()).contains("writes only inside its isolated copy");
        }
    }

    private static HookAnswer write(TestHome home, Worker worker, String file, Path workingDirectory) {
        return home.guardToolCall().checkForWorker(worker.id(), new ToolCall("Write", null, file, workingDirectory));
    }
}
