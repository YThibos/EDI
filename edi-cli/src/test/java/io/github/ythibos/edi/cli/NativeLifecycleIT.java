package io.github.ythibos.edi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.testing.TestProcesses;

@DisplayName("edi up starts the native core once per home and edi down stops it")
class NativeLifecycleIT {

    private static final Duration PATIENCE = Duration.ofSeconds(90);

    @RegisterExtension
    private static final TestProcesses processes = new TestProcesses();

    @TempDir
    private static Path home;

    @AfterAll
    static void stopACoreATestLeftRunning() throws Exception {
        Path pidFile = home.resolve("state/core.pid");
        if (Files.exists(pidFile) && !Files.readString(pidFile).isBlank()) {
            ProcessHandle.of(Long.parseLong(Files.readString(pidFile).strip())).ifPresent(ProcessHandle::destroyForcibly);
        }
    }

    @Test
    void theCommandStartsTheCoreOnceAndStopsIt() throws Exception {
        CommandRun up = edi("up", "--core-only");
        CommandRun health = edi("health");
        CommandRun again = edi("up", "--core-only");
        CommandRun second = run(new ProcessBuilder(binary("edi.core.binary").toString()).redirectErrorStream(true));
        CommandRun down = edi("down");
        CommandRun afterwards = edi("health");

        assertThat(up.output()).isEqualTo("EDI core is up.\n");
        assertThat(health.output()).startsWith("EDI core is up on 127.0.0.1:");
        assertThat(again.output()).isEqualTo("EDI core is already up.\n");
        assertThat(second.exitCode()).isNotZero();
        assertThat(second.output()).contains("Another EDI core runs on this home");
        assertThat(down.exitCode()).isZero();
        assertThat(down.output()).isEqualTo("EDI core stops.\n");
        assertThat(afterwards.exitCode()).isOne();
    }

    private static CommandRun edi(String... arguments) throws Exception {
        List<String> commandLine = new ArrayList<>(List.of(binary("edi.cli.binary").toString()));
        commandLine.addAll(List.of(arguments));
        return run(new ProcessBuilder(commandLine).redirectErrorStream(true));
    }

    private static CommandRun run(ProcessBuilder launch) throws Exception {
        launch.environment().keySet().removeAll(List.of("TMUX", "TMUX_PANE", "CLAUDE_CODE_SESSION_ID", "CLAUDE_PID"));
        launch.environment().put("EDI_HOME", home.toString());
        launch.environment().put("EDI_CORE", binary("edi.core.binary").toString());
        launch.environment().put("SERVER_PORT", "0");
        Process command = processes.register(launch.start());
        try (OutputStream standardInput = command.getOutputStream()) {
            standardInput.write(new byte[0]);
        }
        String output = new String(command.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!command.waitFor(PATIENCE.toSeconds(), TimeUnit.SECONDS)) {
            command.destroyForcibly();
        }
        return new CommandRun(command.exitValue(), output);
    }

    private static Path binary(String property) {
        return Path.of(System.getProperty(property));
    }

    private record CommandRun(int exitCode, String output) {
    }
}
