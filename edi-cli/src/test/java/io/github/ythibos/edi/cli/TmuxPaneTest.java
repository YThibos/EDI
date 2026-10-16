package io.github.ythibos.edi.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.testing.TestProcesses;
import picocli.CommandLine;

@Tag("adapter")
class TmuxPaneTest {

    private static final Duration PATIENCE = Duration.ofSeconds(10);
    private static final int PANE_COLUMNS = 90;

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path home;

    private StubCore core;
    private final String server = "edi-test-" + UUID.randomUUID();
    private String socket;
    private ProcessHandle serverProcess;

    @BeforeEach
    void startTheStubCore() throws IOException {
        core = StubCore.recordedIn(home).answering(200, "report\n");
    }

    @AfterEach
    void stopTheStubCoreAndThePrivateTmuxServer() throws Exception {
        core.close();
        if (socket != null) {
            run(tmux("kill-server"));
            TestProcesses.awaitExit(serverProcess);
            Files.deleteIfExists(Path.of(socket));
        }
    }

    @Test
    void sendsTheWidthOfThePaneTheCommandRunsIn() throws Exception {
        assumeTrue(isInstalled(), "tmux is not installed");
        assertThat(run(tmux("-f", "/dev/null", "new-session", "-d", "-s", "probe", "-x", Integer.toString(PANE_COLUMNS), "-y", "24")).exitCode()).isZero();
        socket = run(tmux("display-message", "-p", "-t", "probe", "#{socket_path}")).output().strip();
        serverProcess = ProcessHandle.of(Long.parseLong(run(tmux("display-message", "-p", "-t", "probe", "#{pid}")).output().strip())).orElseThrow();
        processes.register(serverProcess.pid());
        String pane = run(tmux("display-message", "-p", "-t", "probe", "#{pane_id}")).output().strip();

        int exitCode = edi(Map.of("EDI_HOME", home.toString(), "TMUX", socket + ",0,0", "TMUX_PANE", pane), "sitrep");

        assertThat(exitCode).isZero();
        assertThat(core.received().target()).isEqualTo("/api/report?paneWidth=" + PANE_COLUMNS);
    }

    @Test
    void sendsNoWidthWhenThePaneDoesNotAnswer() {
        int exitCode = edi(Map.of("EDI_HOME", home.toString(), "TMUX", home.resolve("no-server") + ",0,0", "TMUX_PANE", "%0"), "report");

        assertThat(exitCode).isZero();
        assertThat(core.received().target()).isEqualTo("/api/report");
    }

    private boolean isInstalled() {
        try {
            return run(List.of("tmux", "-V")).exitCode() == 0;
        } catch (Exception missing) {
            return false;
        }
    }

    private List<String> tmux(String... arguments) {
        return Stream.concat(Stream.of("tmux", "-L", server), Arrays.stream(arguments)).toList();
    }

    private int edi(Map<String, String> environment, String... arguments) {
        return new CommandLine(new Edi(environment, InputStream.nullInputStream()))
                .setOut(new PrintWriter(new StringWriter(), true))
                .setErr(new PrintWriter(new StringWriter(), true))
                .execute(arguments);
    }

    private ProgramRun run(List<String> commandLine) throws IOException, InterruptedException {
        Process program = processes.register(new ProcessBuilder(commandLine).redirectErrorStream(true).start());
        program.getOutputStream().close();
        if (!program.waitFor(PATIENCE.toSeconds(), TimeUnit.SECONDS)) {
            program.destroyForcibly();
            throw new IOException(String.join(" ", commandLine) + " did not finish within " + PATIENCE.toSeconds() + " seconds");
        }
        return new ProgramRun(program.exitValue(), new String(program.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    private record ProgramRun(int exitCode, String output) {
    }
}
