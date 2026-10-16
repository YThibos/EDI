package io.github.ythibos.edi.adapter.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.domain.worker.WorkerWindow;
import io.github.ythibos.edi.testing.InstalledPrograms;
import tools.jackson.databind.json.JsonMapper;

public final class PrivateTmuxServer implements AutoCloseable {

    private static final Duration PATIENCE = Duration.ofSeconds(10);
    private static final Duration POLL = Duration.ofMillis(50);

    private final Tmux tmux;

    private PrivateTmuxServer(Tmux tmux) {
        this.tmux = tmux;
    }

    public static boolean isInstalled() {
        return InstalledPrograms.answers("tmux", "-V");
    }

    public static PrivateTmuxServer started() {
        return new PrivateTmuxServer(new Tmux(new ExternalPrograms(), "edi-test-" + UUID.randomUUID()));
    }

    public String socket() {
        return tmux.socket();
    }

    public TmuxClaudeRuntime runtime(Path claudeConfiguration, String claudeCode) {
        JsonMapper json = new JsonMapper();
        return new TmuxClaudeRuntime(tmux, new TmuxPaneProbe(), new ClaudeWorkspaceTrust(claudeConfiguration, json), json, claudeCode);
    }

    public WorkerWindow windowShowing(String fixture, Path directory, Path received) throws IOException {
        Path screen = directory.resolve(fixture);
        try (InputStream content = PrivateTmuxServer.class.getResourceAsStream("/pane-probe/" + fixture)) {
            Files.write(screen, content.readAllBytes());
        }
        Tmux.OpenedWindow opened = tmux.openWindow(fixture, directory, Map.of(),
                List.of("sh", "-c", "cat \"$0\"; exec cat > \"$1\"", screen.toString(), received.toString()));
        Tmux.ServerIdentity server = tmux.server();
        WorkerWindow window = new WorkerWindow(tmux.socket(), server.pid(), server.startedAt(), opened.windowId(), opened.panePid());
        awaitLines(window, lines -> lines.stream().anyMatch(line -> !line.isBlank()));
        return window;
    }

    public List<String> visibleLines(WorkerWindow window) {
        return tmux.visibleLines(window.windowId());
    }

    public static String awaitContent(Path file, String expected) throws IOException {
        long deadline = System.nanoTime() + PATIENCE.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(file) && Files.readString(file, StandardCharsets.UTF_8).contains(expected)) {
                return Files.readString(file, StandardCharsets.UTF_8);
            }
            pause();
        }
        return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
    }

    @Override
    public void close() throws IOException {
        tmuxOnThisSocket("kill-server");
        Files.deleteIfExists(socketFile());
    }

    private Path socketFile() throws IOException {
        String socketDirectory = System.getenv().getOrDefault("TMUX_TMPDIR", "/tmp");
        Object uid = Files.getAttribute(Path.of("/proc/self"), "unix:uid");
        return Path.of(socketDirectory, "tmux-" + uid, tmux.socket());
    }

    private String tmuxOnThisSocket(String... arguments) throws IOException {
        List<String> command = new ArrayList<>(List.of("tmux", "-L", tmux.socket()));
        command.addAll(List.of(arguments));
        Process tmuxRun = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String output = new String(tmuxRun.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            tmuxRun.waitFor(PATIENCE.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return output;
    }

    public List<String> awaitLines(WorkerWindow window, Predicate<List<String>> shown) {
        long deadline = System.nanoTime() + PATIENCE.toNanos();
        List<String> lines = visibleLines(window);
        while (System.nanoTime() < deadline && !shown.test(lines)) {
            pause();
            lines = visibleLines(window);
        }
        return lines;
    }

    private static void pause() {
        try {
            Thread.sleep(POLL);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
