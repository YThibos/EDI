package io.github.ythibos.edi.adapter.runtime;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.Program;
import io.github.ythibos.edi.adapter.runtime.Tmux.OpenedWindow;
import io.github.ythibos.edi.adapter.runtime.Tmux.ServerIdentity;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.worker.ErrorStop;
import io.github.ythibos.edi.domain.worker.Keystroke;
import io.github.ythibos.edi.domain.worker.LimitStop;
import io.github.ythibos.edi.domain.worker.PaneState;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import io.github.ythibos.edi.domain.worker.WindowCheck;
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
    private final ClaudeTranscript transcripts;
    private final ClaudeSessions sessions;
    private final String claudeCode;
    private final String mise;
    private final MiseTrust miseTrust;
    private final ClaudeLoginReadiness readiness;
    private final String edi;

    public TmuxClaudeRuntime(Tmux tmux, TmuxPaneProbe probe, ClaudeWorkspaceTrust trust, ClaudeSessions sessions, ClaudeLoginFiles logins, JsonMapper json,
            ExternalPrograms programs, @Value("${edi.workers.claude-code:}") String claudeCode, @Value("${edi.workers.mise:}") String mise,
            @Value("${edi.workers.edi:}") String edi) {
        this.tmux = tmux;
        this.probe = probe;
        this.trust = trust;
        this.json = json;
        this.transcripts = new ClaudeTranscript(json);
        this.sessions = sessions;
        this.claudeCode = claudeCode.isBlank() ? Program.CLAUDE_CODE.executable() : claudeCode;
        this.mise = mise.isBlank() ? Program.MISE.executable() : mise;
        this.miseTrust = new MiseTrust(programs, this.mise, Path.of(System.getProperty("user.home")));
        this.readiness = new ClaudeLoginReadiness(programs, this.claudeCode, logins, json);
        this.edi = edi;
    }

    @Override
    public String socket() {
        return tmux.socket();
    }

    @Override
    public void requireReady(Login login) {
        readiness.requireReady(login);
    }

    @Override
    public WorkerWindow launch(Launch launch) {
        LocalHooks.refuseHooksIn(launch.worktree(), json);
        trust.trust(launch.projectClone(), launch.login());
        Path settings = WorkerSettingsFile.writtenInto(launch.stateDirectory(), ediCommand(), json);
        List<String> claude = ClaudeCommandLine.of(claudeCode, mise, miseTrust.trusts(launch.projectClone()), launch, settings,
                ClaudeLayer.read(ClaudeLayer.WORKER_SYSTEM_PROMPT), System.getenv().keySet());
        OpenedWindow opened = tmux.openWindow(launch.worker().task().value(), launch.worktree(), ClaudeCommandLine.windowEnvironment(launch), claude);
        ServerIdentity server = tmux.server();
        return new WorkerWindow(tmux.socket(), server.pid(), server.startedAt(), opened.windowId(), opened.panePid());
    }

    @Override
    public WindowCheck check(WorkerWindow window) {
        Optional<ServerIdentity> server = tmux.runningServer();
        if (server.isEmpty()) {
            return WindowCheck.SERVER_GONE;
        }
        if (!server.get().equals(new ServerIdentity(window.serverPid(), window.serverStartedAt()))) {
            return WindowCheck.SERVER_CHANGED;
        }
        return tmux.windowIds().contains(window.windowId()) ? WindowCheck.OPEN : WindowCheck.WINDOW_CLOSED;
    }

    @Override
    public PaneReading peek(WorkerWindow window) {
        requireRunning(window);
        List<String> lines = tmux.visibleLines(window.windowId());
        return new PaneReading(probe.classify(lines), lines);
    }

    @Override
    public List<String> mirror(WorkerWindow window) {
        requireRunning(window);
        return tmux.visibleLinesWithColours(window.windowId());
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

    @Override
    public boolean hasATranscriptOf(UUID session) {
        return sessions.transcriptOf(session).isPresent();
    }

    @Override
    public Optional<LimitStop> usageLimitStopIn(String transcript, Instant since) {
        return transcript == null ? Optional.empty() : transcripts.usageLimitStop(Path.of(transcript), since);
    }

    @Override
    public Optional<Instant> contextLimitStopIn(String transcript, Instant since) {
        return transcript == null ? Optional.empty() : transcripts.contextLimitStop(Path.of(transcript), since);
    }

    @Override
    public Optional<Instant> missedStopIn(String transcript, Instant since) {
        return transcript == null ? Optional.empty() : transcripts.missedStop(Path.of(transcript), since);
    }

    @Override
    public Optional<ErrorStop> errorStopIn(String transcript, Instant since) {
        return transcript == null ? Optional.empty() : transcripts.errorStop(Path.of(transcript), since);
    }

    @Override
    public TokenUsage tokensSpentIn(String transcript, Instant since) {
        return transcript == null ? TokenUsage.NONE : transcripts.tokensSpent(Path.of(transcript), since);
    }

    private Path ediCommand() {
        if (!edi.isBlank()) {
            return Path.of(edi);
        }
        return EdiOnThePath.in(System.getenv("PATH")).orElseThrow(() -> new Refusal("The edi command is on none of the directories of the core's PATH,"
                + " and a worker's hooks call it by its absolute path; put edi on that PATH or set edi.workers.edi to it."));
    }

    private void typeIntoTheIdlePrompt(WorkerWindow window, Keystroke.Line line) {
        PaneState state = peek(window).state();
        if (!state.acceptsALine()) {
            throw new Refusal("The worker's pane shows " + state.word() + ", not the idle prompt, so nothing is typed into it.");
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
                throw new Refusal("A dialog opened over the worker's composer, so nothing more is pressed.");
            }
        }
        throw new Refusal("The worker's composer kept the line after " + SUBMIT_ATTEMPTS + " presses of Enter.");
    }

    private static void settle() {
        try {
            Thread.sleep(SETTLE);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new Refusal("Typing into the worker's pane was interrupted.");
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
