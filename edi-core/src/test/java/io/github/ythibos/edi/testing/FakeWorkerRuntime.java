package io.github.ythibos.edi.testing;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.worker.ErrorStop;
import io.github.ythibos.edi.domain.worker.Keystroke;
import io.github.ythibos.edi.domain.worker.LimitStop;
import io.github.ythibos.edi.domain.worker.LoginName;
import io.github.ythibos.edi.domain.worker.PaneState;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import io.github.ythibos.edi.domain.worker.WindowCheck;
import io.github.ythibos.edi.domain.worker.WorkerWindow;

public final class FakeWorkerRuntime implements WorkerRuntime {

    public static final String SOCKET = "edi";

    private static final String CREDENTIALS = ".credentials.json";

    private final List<Launch> launches = new ArrayList<>();
    private final Set<String> openWindows = new HashSet<>();
    private final List<String> typedLines = new ArrayList<>();
    private final Set<UUID> sessionsWithoutATranscript = new HashSet<>();
    private final Map<LoginName, String> unreadyLogins = new HashMap<>();
    private final List<Login> readinessChecks = new ArrayList<>();
    private final List<Login> transcriptLookups = new ArrayList<>();
    private LoginMismatch loginMismatch;
    private Path claudeCodesOwnDirectory = Path.of("/nonexistent/claude-home/.claude");
    private PaneState pane = PaneState.IDLE_PROMPT;
    private List<String> mirrored = List.of("\u001b[1m❯\u001b[0m");
    private int mirrors;
    private boolean paneFails;
    private boolean serverRunning = true;
    private long serverPid = 4242;
    private Instant serverStartedAt = Instant.parse("2026-10-15T08:00:00Z");
    private LimitStop limitStopInTheTranscript;
    private Instant contextFullInTheTranscript;
    private Instant missedStopInTheTranscript;
    private ErrorStop errorStopInTheTranscript;
    private TokenUsage tokensInTheTranscript = TokenUsage.NONE;
    private Runnable duringTheNextLaunch = () -> {
    };

    @Override
    public String socket() {
        return SOCKET;
    }

    @Override
    public synchronized void requireReady(Login login) {
        readinessChecks.add(login);
        if (unreadyLogins.containsKey(login.name())) {
            throw new Refusal(Rule.CLAUDE_LOGIN, unreadyLogins.get(login.name()));
        }
    }

    @Override
    public synchronized WorkerWindow launch(Launch launch) {
        launches.add(launch);
        Runnable during = duringTheNextLaunch;
        duringTheNextLaunch = () -> {
        };
        during.run();
        return openWindow();
    }

    public synchronized WorkerWindow openWindow() {
        serverRunning = true;
        String window = "@" + (openWindows.size() + launches.size() + 1);
        openWindows.add(window);
        return new WorkerWindow(SOCKET, serverPid, serverStartedAt, window, 5000L + openWindows.size());
    }

    public synchronized WorkerWindow openWindowOf(long panePid, long servedBy) {
        serverRunning = true;
        serverPid = servedBy;
        String window = "@" + (openWindows.size() + launches.size() + 1);
        openWindows.add(window);
        return new WorkerWindow(SOCKET, serverPid, serverStartedAt, window, panePid);
    }

    @Override
    public synchronized WindowCheck check(WorkerWindow window) {
        if (!serverRunning) {
            return WindowCheck.SERVER_GONE;
        }
        if (window.serverPid() != serverPid || !window.serverStartedAt().equals(serverStartedAt)) {
            return WindowCheck.SERVER_CHANGED;
        }
        return openWindows.contains(window.windowId()) ? WindowCheck.OPEN : WindowCheck.WINDOW_CLOSED;
    }

    @Override
    public synchronized PaneReading peek(WorkerWindow window) {
        return new PaneReading(pane, List.of("❯"));
    }

    @Override
    public synchronized List<String> mirror(WorkerWindow window) {
        mirrors++;
        if (!openWindows.contains(window.windowId())) {
            throw new Refusal(Rule.PANE_TYPING, "The worker's tmux window " + window.windowId() + " is no longer on the " + window.socket() + " server.");
        }
        return mirrored;
    }

    public synchronized int mirrors() {
        return mirrors;
    }

    public synchronized void showsInThePane(List<String> lines) {
        mirrored = List.copyOf(lines);
    }

    @Override
    public synchronized void press(WorkerWindow window, Keystroke keystroke) {
        if (paneFails) {
            throw new ProgramFailure("tmux exited with 1: can't find pane");
        }
        if (keystroke instanceof Keystroke.Line line) {
            if (!pane.acceptsALine()) {
                throw new Refusal(Rule.PANE_TYPING, "The pane shows " + pane.word() + ".");
            }
            typedLines.add(line.text());
        }
    }

    @Override
    public synchronized void close(WorkerWindow window) {
        openWindows.remove(window.windowId());
    }

    @Override
    public synchronized boolean hasATranscriptOf(UUID session, Login login) {
        transcriptLookups.add(login);
        return !sessionsWithoutATranscript.contains(session);
    }

    @Override
    public synchronized Optional<LoginMismatch> loginMismatchOf(String transcript, Login login) {
        return transcript == null ? Optional.empty() : Optional.ofNullable(loginMismatch);
    }

    @Override
    public synchronized Path credentialsOf(Login login) {
        return (login.keepsClaudeCodesOwnDirectory() ? claudeCodesOwnDirectory : login.configDirectory()).resolve(CREDENTIALS);
    }

    @Override
    public synchronized Optional<LimitStop> usageLimitStopIn(String transcript, Instant since) {
        return Optional.ofNullable(limitStopInTheTranscript).filter(stop -> !stop.endedAt().isBefore(since));
    }

    @Override
    public synchronized Optional<Instant> contextLimitStopIn(String transcript, Instant since) {
        return Optional.ofNullable(contextFullInTheTranscript).filter(at -> !at.isBefore(since));
    }

    @Override
    public synchronized Optional<Instant> missedStopIn(String transcript, Instant since) {
        return Optional.ofNullable(missedStopInTheTranscript).filter(at -> !at.isBefore(since));
    }

    @Override
    public synchronized Optional<ErrorStop> errorStopIn(String transcript, Instant since) {
        return Optional.ofNullable(errorStopInTheTranscript).filter(stop -> !stop.endedAt().isBefore(since));
    }

    @Override
    public synchronized TokenUsage tokensSpentIn(String transcript, Instant since) {
        return tokensInTheTranscript;
    }

    public synchronized void showing(PaneState state) {
        pane = state;
    }

    public synchronized void failingEveryKeystroke() {
        paneFails = true;
    }

    public synchronized void loginIsNotReady(LoginName login, String refusal) {
        unreadyLogins.put(login, refusal);
    }

    public synchronized List<Login> readinessChecks() {
        return List.copyOf(readinessChecks);
    }

    public synchronized List<Login> transcriptLookups() {
        return List.copyOf(transcriptLookups);
    }

    public synchronized void runsEverySessionIn(LoginMismatch mismatch) {
        loginMismatch = mismatch;
    }

    public synchronized void keepsClaudeCodesOwnDirectoryIn(Path directory) {
        claudeCodesOwnDirectory = directory;
    }

    public synchronized void leaveNoTranscriptOf(UUID session) {
        sessionsWithoutATranscript.add(session);
    }

    public synchronized void closeEveryWindow() {
        openWindows.clear();
    }

    public synchronized void restartTheServer() {
        openWindows.clear();
        serverPid += 1;
        serverStartedAt = serverStartedAt.plusSeconds(60);
    }

    public synchronized void loseTheServer() {
        openWindows.clear();
        serverRunning = false;
    }

    public synchronized void duringTheNextLaunch(Runnable meanwhile) {
        duringTheNextLaunch = meanwhile;
    }

    public synchronized void transcriptEndsOn(LimitStop stop) {
        limitStopInTheTranscript = stop;
    }

    public synchronized void transcriptEndsOnAFullContextAt(Instant at) {
        contextFullInTheTranscript = at;
    }

    public synchronized void transcriptEndsOnAStopEdiMissedAt(Instant at) {
        missedStopInTheTranscript = at;
    }

    public synchronized void transcriptEndsOn(ErrorStop stop) {
        errorStopInTheTranscript = stop;
    }

    public synchronized void transcriptSpent(TokenUsage tokens) {
        tokensInTheTranscript = tokens;
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
