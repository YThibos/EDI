package io.github.ythibos.edi.application.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
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
import tools.jackson.databind.json.JsonMapper;

class SecretPathsTest {

    @TempDir
    private Path directory;

    private TestHome home;
    private Worker worker;
    private Path worktree;
    private Path state;

    @BeforeEach
    void serveAWorkerBesideTheHomeToken() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        worktree = Files.createDirectories(home.home().worktreeOf(new ProjectName("harness"), new TaskId("fit-lamp")));
        Files.createDirectories(home.home().coreTokenFile().getParent());
        Files.writeString(home.home().coreTokenFile(), "home-token-for-the-test-only\n");
        state = home.home().coreTokenFile().getParent();
    }

    @Test
    void refusesAWorkerTheHomeTokenHoweverItsPathIsWritten() throws IOException {
        Files.createSymbolicLink(worktree.resolve("harbour"), state);
        List<String> commands = List.of(
                "cat " + state + "/core.tok*",
                "cat " + state + "/core.t?ken",
                "head -c 64 " + state + "/core.{token,lock}",
                "cat " + state + "//core.token",
                "cat " + state + "/./core.token",
                "cat " + state + "/../state/core.token",
                "cat harbour/core.token",
                "cat ./harbour/core.token",
                "base64 < harbour/core.token");

        assertThat(commands).filteredOn(command -> !shell(command).reason().contains("names a file that holds a secret")).isEmpty();
    }

    @Test
    void letsAWorkerReadItsOwnSourcesAndListItsCopy() throws IOException {
        Files.writeString(worktree.resolve("Lamp.java"), "class Lamp {}\n");
        for (String command : List.of("cat Lamp.java", "ls *.java", "grep -rn token src", "cat " + state + "/core.port", "ls " + state)) {
            assertThat(shell(command)).as(command).isEqualTo(HookAnswer.PROCEED);
        }
    }

    @Test
    void sendsAWorkersGlobToTheGuard() throws IOException {
        try (InputStream settings = SecretPathsTest.class.getClassLoader().getResourceAsStream("claude/worker/settings.json")) {
            String matcher = new JsonMapper().readTree(settings).path("hooks").path("PreToolUse").path(0).path("matcher").asString();
            assertThat(List.of(matcher.split("\\|"))).contains("Glob");
        }
    }

    @Test
    void refusesAWorkerAFileSearchThatReachesTheHomeToken() {
        for (ToolCall search : List.of(new ToolCall("Glob", null, state.toString(), worktree, "*"),
                new ToolCall("Glob", null, home.home().root().toString(), worktree, "**/core.tok*"),
                new ToolCall("Glob", null, state.toString(), worktree, "**/*.token"),
                new ToolCall("Glob", null, null, worktree, state + "/core.{token,port}"))) {
            HookAnswer answer = home.guardToolCall().checkForWorker(worker.id(), search);
            assertThat(answer.verdict()).as(search.toString()).isEqualTo(HookAnswer.Verdict.DENY_THE_TOOL);
            assertThat(answer.reason()).as(search.toString()).contains("this file search touches a secret");
        }
    }

    @Test
    void letsAWorkerSearchItsOwnCopy() {
        for (ToolCall search : List.of(new ToolCall("Glob", null, null, worktree, "**/*.java"), new ToolCall("Glob", null, state.toString(), worktree, "*.port"))) {
            assertThat(home.guardToolCall().checkForWorker(worker.id(), search)).as(search.toString()).isEqualTo(HookAnswer.PROCEED);
        }
    }

    private HookAnswer shell(String command) {
        return home.guardToolCall().checkForWorker(worker.id(), new ToolCall("Bash", command, null, worktree));
    }
}
