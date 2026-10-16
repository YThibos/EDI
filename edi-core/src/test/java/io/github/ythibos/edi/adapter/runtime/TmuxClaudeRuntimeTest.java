package io.github.ythibos.edi.adapter.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.application.port.WorkerRuntime.Launch;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.PermissionMode;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.domain.worker.WorkerWindow;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Tag("adapter")
class TmuxClaudeRuntimeTest {

    private static final String RECORDING_CLAUDE = """
            #!/bin/sh
            record="$EDI_HOME/claude-was-started"
            pwd -P > "$record.cwd"
            printf '%s\\n' "$EDI_ROLE $EDI_TASK_ID $EDI_WORKER $EDI_TOKEN_FILE ${CLAUDECODE:-no-parent-session}" > "$record.environment"
            for word in "$@"; do printf '%s\\0' "$word"; done > "$record.arguments"
            exec sleep 30
            """;

    @TempDir
    private Path directory;

    private EdiHome home;
    private Path worktree;
    private Path claudeConfiguration;

    @BeforeEach
    void prepareAWorktreeAndAClaudeThatRecordsHowItStarted() throws IOException {
        assumeTrue(PrivateTmuxServer.isInstalled(), "tmux is not installed");
        home = new EdiHome(directory.resolve("home"));
        worktree = Files.createDirectories(home.worktrees().resolve("harness/fit-lamp"));
        claudeConfiguration = Files.createDirectories(directory.resolve("claude-config"));
        Files.writeString(claudeConfiguration.resolve(".claude.json"), "{\"numStartups\": 3, \"projects\": {\"/elsewhere\": {\"allowedTools\": []}}}");
        Path claude = Files.writeString(directory.resolve("claude"), RECORDING_CLAUDE);
        Files.setPosixFilePermissions(claude, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    @Test
    void launchesClaudeCodeInTheWorktreeWithItsSessionRouteSettingsAndEnvironment() throws IOException {
        try (PrivateTmuxServer server = PrivateTmuxServer.started()) {
            TmuxClaudeRuntime runtime = server.runtime(claudeConfiguration, directory.resolve("claude").toString());
            UUID session = UUID.randomUUID();

            WorkerWindow window = runtime.launch(launch(session, List.of("WebSearch", "Bash(git push:*)")));

            assertThat(window.socket()).isEqualTo(server.socket());
            assertThat(runtime.isRunning(window)).isTrue();
            List<String> arguments = List.of(Files.readString(awaitFile(home.root().resolve("claude-was-started.arguments"))).split("\0"));
            Path settings = home.root().resolve("state/workers/fit-lamp-1/claude-settings.json");
            assertThat(arguments).containsExactly(
                    "--disallowedTools", "WebSearch", "Bash(git push:*)",
                    "--add-dir", home.taskDirectory(new TaskId("fit-lamp")).toString(),
                    "--session-id", session.toString(),
                    "--model", "claude-opus-5-5", "--effort", "high",
                    "--name", "fit-lamp",
                    "--settings", settings.toString(),
                    "--dangerously-skip-permissions",
                    "--append-system-prompt", resource("claude/worker/system-prompt.md"),
                    "--", "\u2063EDI_OP: brief waiting: read the brief and follow it");
            assertThat(Files.readString(home.root().resolve("claude-was-started.cwd")).strip()).isEqualTo(worktree.toRealPath().toString());
            assertThat(Files.readString(home.root().resolve("claude-was-started.environment")).strip())
                    .isEqualTo("worker fit-lamp fit-lamp#1 /tokens/fit-lamp-1 no-parent-session");
            JsonNode written = new JsonMapper().readTree(Files.readString(settings));
            assertThat(written.path("attribution").path("commit").asString()).isEmpty();
            assertThat(written.path("hooks").path("PreToolUse").get(0).path("hooks").get(0).path("command").asString()).isEqualTo("edi hook pre-tool-use");
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(settings))).isEqualTo("rw-------");
            JsonNode claudeConfig = new JsonMapper().readTree(Files.readString(claudeConfiguration.resolve(".claude.json")));
            assertThat(claudeConfig.path("projects").path(projectClone().toString()).path("hasTrustDialogAccepted").asBoolean()).isTrue();
            assertThat(claudeConfig.path("numStartups").asInt()).isEqualTo(3);
            assertThat(claudeConfig.path("projects").has("/elsewhere")).isTrue();

            runtime.close(window);
            assertThat(runtime.isRunning(window)).isFalse();
        }
    }

    @Test
    void refusesAWorktreeWhoseLocalSettingsDefineHooks() throws IOException {
        Files.createDirectories(worktree.resolve(".claude"));
        Files.writeString(worktree.resolve(".claude/settings.local.json"), "{\"hooks\": {\"Stop\": [{\"hooks\": []}]}}");
        try (PrivateTmuxServer server = PrivateTmuxServer.started()) {
            TmuxClaudeRuntime runtime = server.runtime(claudeConfiguration, directory.resolve("claude").toString());

            assertThatThrownBy(() -> runtime.launch(launch(UUID.randomUUID(), List.of())))
                    .isInstanceOf(Refusal.class).hasMessageContaining("defines hooks, which Claude Code would run beside EDI's own");
        }
    }

    private Launch launch(UUID session, List<String> disallowedTools) {
        WorkerId worker = WorkerId.first(new TaskId("fit-lamp"));
        return new Launch(worker, session, new Route(new ModelId("claude-opus-5-5"), EffortLevel.HIGH), worktree, projectClone(), home.taskDirectory(worker.task()),
                home.workerState(worker), Map.of("EDI_HOME", home.root().toString(), "EDI_ROLE", "worker", "EDI_TASK_ID", "fit-lamp",
                        "EDI_WORKER", worker.toString(), "EDI_TOKEN_FILE", "/tokens/fit-lamp-1"),
                disallowedTools, PermissionMode.BYPASS, "\u2063EDI_OP: brief waiting: read the brief and follow it");
    }

    private Path projectClone() {
        return home.root().resolve("projects/harness");
    }

    private static Path awaitFile(Path file) throws IOException {
        PrivateTmuxServer.awaitContent(file, "--");
        return file;
    }

    private static String resource(String name) throws IOException {
        try (InputStream content = TmuxClaudeRuntimeTest.class.getClassLoader().getResourceAsStream(name)) {
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
