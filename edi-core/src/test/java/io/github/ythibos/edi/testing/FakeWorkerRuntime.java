package io.github.ythibos.edi.testing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.worker.Keystroke;
import io.github.ythibos.edi.domain.worker.PaneState;
import io.github.ythibos.edi.domain.worker.WorkerWindow;

public final class FakeWorkerRuntime implements WorkerRuntime {

    public static final String SOCKET = "edi";

    private final List<Launch> launches = new ArrayList<>();
    private final Set<String> openWindows = new HashSet<>();
    private final List<String> typedLines = new ArrayList<>();
    private PaneState pane = PaneState.IDLE_PROMPT;

    @Override
    public String socket() {
        return SOCKET;
    }

    @Override
    public synchronized WorkerWindow launch(Launch launch) {
        launches.add(launch);
        return openWindow();
    }

    public synchronized WorkerWindow openWindow() {
        String window = "@" + (openWindows.size() + launches.size() + 1);
        openWindows.add(window);
        return new WorkerWindow(SOCKET, 4242, Instant.parse("2026-10-15T08:00:00Z"), window, 5000L + openWindows.size());
    }

    @Override
    public synchronized boolean isRunning(WorkerWindow window) {
        return openWindows.contains(window.windowId());
    }

    @Override
    public synchronized PaneReading peek(WorkerWindow window) {
        return new PaneReading(pane, List.of("❯"));
    }

    @Override
    public synchronized void press(WorkerWindow window, Keystroke keystroke) {
        if (keystroke instanceof Keystroke.Line line) {
            if (!pane.acceptsALine()) {
                throw new Refusal("The pane shows " + pane.word() + ".");
            }
            typedLines.add(line.text());
        }
    }

    @Override
    public synchronized void close(WorkerWindow window) {
        openWindows.remove(window.windowId());
    }

    public synchronized void showing(PaneState state) {
        pane = state;
    }

    public synchronized void closeEveryWindow() {
        openWindows.clear();
    }

    public synchronized List<Launch> launches() {
        return List.copyOf(launches);
    }

    public synchronized Launch lastLaunch() {
        return launches.getLast();
    }

    public synchronized List<String> typedLines() {
        return List.copyOf(typedLines);
    }
}
