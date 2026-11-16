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
import java.util.Arrays;
import java.util.HashMap;
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

import io.github.ythibos.edi.testing.Patience;
import io.github.ythibos.edi.testing.TestProcesses;
import picocli.CommandLine;

@Tag("adapter")
class TmuxPaneTest {

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

        int exitCode = edi(inAPane(), "sitrep");

        assertThat(exitCode).isZero();
        assertThat(core.received().target()).isEqualTo("/api/report?paneWidth=" + PANE_COLUMNS);
    }

    @Test
    void sizesThePrimarysStopForItsPaneButNotAWorkersStop() throws Exception {
        assumeTrue(isInstalled(), "tmux is not installed");
        Map<String, String> primary = inAPane();
        Map<String, String> worker = new HashMap<>(primary);
        worker.put("EDI_TOKEN_FILE", home.resolve("state/core.token").toString());

        edi(primary, "hook", "stop");
        String primaryStop = core.received().target();
        edi(worker, "hook", "stop");
        String workerStop = core.received().target();

        assertThat(primaryStop).isEqualTo("/api/hooks/stop?paneWidth=" + PANE_COLUMNS);
        assertThat(workerStop).isEqualTo("/api/hooks/stop");
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

    private Map<String, String> inAPane() throws Exception {
        assertThat(run(tmux("-f", "/dev/null", "new-session", "-d", "-s", "probe", "-x", Integer.toString(PANE_COLUMNS), "-y", "24")).exitCode()).isZero();
        socket = run(tmux("display-message", "-p", "-t", "probe", "#{socket_path}")).output().strip();
        serverProcess = ProcessHandle.of(Long.parseLong(run(tmux("display-message", "-p", "-t", "probe", "#{pid}")).output().strip())).orElseThrow();
        processes.register(serverProcess.pid());
        String pane = run(tmux("display-message", "-p", "-t", "probe", "#{pane_id}")).output().strip();
        return Map.of("EDI_HOME", home.toString(), "TMUX", socket + ",0,0", "TMUX_PANE", pane);
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
        if (!program.waitFor(Patience.FOR_REAL_WORK.toSeconds(), TimeUnit.SECONDS)) {
            program.destroyForcibly();
            throw new IOException(String.join(" ", commandLine) + " did not finish within " + Patience.FOR_REAL_WORK.toSeconds() + " seconds");
        }
        return new ProgramRun(program.exitValue(), new String(program.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    private record ProgramRun(int exitCode, String output) {
    }
}
