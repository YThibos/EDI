package io.github.ythibos.edi.adapter.runtime;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.process.Program;
import io.github.ythibos.edi.adapter.runtime.Tmux.OpenedWindow;
import io.github.ythibos.edi.adapter.runtime.Tmux.ServerIdentity;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.worker.Keystroke;
import io.github.ythibos.edi.domain.worker.PaneState;
import io.github.ythibos.edi.domain.worker.WorkerWindow;
import tools.jackson.databind.json.JsonMapper;

@Component
@ImportRuntimeHints(ClaudeLayer.class)
public class TmuxClaudeRuntime implements WorkerRuntime {

    private static final Duration SETTLE = Duration.ofMillis(400);
    private static final int SUBMIT_ATTEMPTS = 3;

    private final Tmux tmux;
    private final TmuxPaneProbe probe;
    private final ClaudeWorkspaceTrust trust;
    private final JsonMapper json;
    private final String claudeCode;

    public TmuxClaudeRuntime(Tmux tmux, TmuxPaneProbe probe, ClaudeWorkspaceTrust trust, JsonMapper json,
            @Value("${edi.workers.claude-code:}") String claudeCode) {
        this.tmux = tmux;
        this.probe = probe;
        this.trust = trust;
        this.json = json;
        this.claudeCode = claudeCode.isBlank() ? Program.CLAUDE_CODE.executable() : claudeCode;
    }

    @Override
    public String socket() {
        return tmux.socket();
    }

    @Override
    public WorkerWindow launch(Launch launch) {
        LocalHooks.refuseHooksIn(launch.worktree(), json);
        trust.trust(launch.projectClone());
        Path settings = WorkerSettingsFile.writtenInto(launch.stateDirectory());
        List<String> claude = ClaudeCommandLine.of(claudeCode, launch, settings, ClaudeLayer.read(ClaudeLayer.WORKER_SYSTEM_PROMPT),
                System.getenv().keySet());
        OpenedWindow opened = tmux.openWindow(launch.worker().task().value(), launch.worktree(), launch.environment(), claude);
        ServerIdentity server = tmux.server();
        return new WorkerWindow(tmux.socket(), server.pid(), server.startedAt(), opened.windowId(), opened.panePid());
    }

    @Override
    public boolean isRunning(WorkerWindow window) {
        return tmux.serves(new ServerIdentity(window.serverPid(), window.serverStartedAt())) && tmux.windowIds().contains(window.windowId());
    }

    @Override
    public PaneReading peek(WorkerWindow window) {
        requireRunning(window);
        List<String> lines = tmux.visibleLines(window.windowId());
        return new PaneReading(probe.classify(lines), lines);
    }

    @Override
    public void press(WorkerWindow window, Keystroke keystroke) {
        switch (keystroke) {
            case Keystroke.Line line -> typeIntoTheIdlePrompt(window, line);
            case Keystroke.Escape _ -> pressEscape(window);
        }
    }

    @Override
    public void close(WorkerWindow window) {
        if (isRunning(window)) {
            tmux.closeWindow(window.windowId());
        }
    }

    private void typeIntoTheIdlePrompt(WorkerWindow window, Keystroke.Line line) {
        PaneState state = peek(window).state();
        if (!state.acceptsALine()) {
            throw new Refusal("The worker's pane shows " + state.word() + ", not the idle prompt, so EDI types nothing into it.");
        }
        tmux.typeLiterally(window.windowId(), line.text());
        submit(window, line.text());
    }

    private void submit(WorkerWindow window, String typed) {
        for (int attempt = 1; attempt <= SUBMIT_ATTEMPTS; attempt++) {
            settle();
            tmux.pressEnter(window.windowId());
            settle();
            List<String> lines = tmux.visibleLines(window.windowId());
            if (!probe.holdsInTheComposer(lines, typed)) {
                return;
            }
            if (probe.classify(lines).isADialog()) {
                throw new Refusal("A dialog opened over the worker's composer, so EDI presses nothing more.");
            }
        }
        throw new Refusal("The worker's composer kept the line after " + SUBMIT_ATTEMPTS + " presses of Enter.");
    }

    private static void settle() {
        try {
            Thread.sleep(SETTLE);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new Refusal("EDI was interrupted while it typed into the worker's pane.");
        }
    }

    private void pressEscape(WorkerWindow window) {
        requireRunning(window);
        tmux.pressEscape(window.windowId());
    }

    private void requireRunning(WorkerWindow window) {
        if (!isRunning(window)) {
            throw new Refusal("The worker's tmux window " + window.windowId() + " is no longer on the " + window.socket() + " server.");
        }
    }
}
