package io.github.ythibos.edi.adapter.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.worker.WorkerWindow;
import io.github.ythibos.edi.testing.TestProcesses;

@Tag("adapter")
class TmuxMirrorTest {

    private static final String ESCAPE = "\u001b";

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    @Test
    void mirrorsThePaneWithItsColoursAndTheSameLinesThePeekReads() throws IOException {
        assumeTrue(PrivateTmuxServer.isInstalled(), "tmux is not installed");
        try (PrivateTmuxServer server = PrivateTmuxServer.started(processes)) {
            TmuxClaudeRuntime runtime = server.runtime(directory.resolve("claude"), "claude", "mise");
            WorkerWindow window = server.windowPrinting("\\033[31mThe lamp is lit\\033[0m\\n\\033[1mclaude-opus-5-5 · high\\033[0m\\n", directory);

            List<String> mirrored = runtime.mirror(window);
            List<String> peeked = runtime.peek(window).lines();

            assertThat(String.join("\n", mirrored)).contains(ESCAPE + "[31mThe lamp is lit", ESCAPE + "[1mclaude-opus-5-5 · high");
            assertThat(mirrored.stream().map(line -> line.replaceAll(ESCAPE + "\\[[0-9;]*m", "")).toList()).isEqualTo(peeked);
        }
    }

    @Test
    void refusesToMirrorAWindowThatIsNoLongerOpen() throws IOException {
        assumeTrue(PrivateTmuxServer.isInstalled(), "tmux is not installed");
        try (PrivateTmuxServer server = PrivateTmuxServer.started(processes)) {
            TmuxClaudeRuntime runtime = server.runtime(directory.resolve("claude"), "claude", "mise");
            WorkerWindow window = server.windowPrinting("The lamp is lit\\n", directory);
            runtime.close(window);

            assertThatThrownBy(() -> runtime.mirror(window)).isInstanceOf(Refusal.class).hasMessageContaining("is no longer on the " + server.socket() + " server.");
        }
    }
}
