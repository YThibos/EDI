package io.github.ythibos.edi.application.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class RefusedWorkerReportsTest {

    @TempDir
    private Path directory;

    private TestHome home;
    private Worker worker;
    private Path worktree;
    private String token;

    @BeforeEach
    void serveAWorker() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        worktree = Files.createDirectories(home.home().worktreeOf(new ProjectName("harness"), new TaskId("fit-lamp")));
        token = home.home().coreTokenFile().toString();
    }

    @Test
    void letsARefusedWorkerReportThatItIsBlocked() {
        assertThat(shell("cat " + token).verdict()).isEqualTo(HookAnswer.Verdict.DENY_THE_TOOL);

        for (String report : List.of("edi status blocked --key token -- the guard refused cat " + token,
                "edi status blocked --key token -- 'the guard refused cat " + token + " > /dev/null'",
                "edi status needs-decision --key lamp -- \"read the token at " + token + " or stop\"")) {
            assertThat(shell(report)).as(report).isEqualTo(HookAnswer.PROCEED);
        }
    }

    @Test
    void judgesAReportThatDoesMoreThanReport() {
        for (String command : List.of("edi status blocked --key token -- stuck; cat " + token,
                "edi status blocked --key token - < " + token,
                "edi status blocked --key token -- \"$(cat " + token + ")\"",
                "edi status working -- done && pkill java",
                "EDI_ROLE=primary edi status working -- done",
                "edi status working -- done > .claude/settings.local.json",
                "edi status blocked @" + token,
                "edi status blocked --key token -- @" + token,
                "./edi status blocked --key token -- " + token,
                worktree.resolve("edi") + " status blocked --key token -- " + token)) {
            assertThat(shell(command).verdict()).as(command).isEqualTo(HookAnswer.Verdict.DENY_THE_TOOL);
        }
    }

    private HookAnswer shell(String command) {
        return home.guardToolCall().checkForWorker(worker.id(), new ToolCall("Bash", command, null, worktree));
    }
}
