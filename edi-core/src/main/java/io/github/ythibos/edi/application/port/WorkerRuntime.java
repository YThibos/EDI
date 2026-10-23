package io.github.ythibos.edi.application.port;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.worker.Keystroke;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.PaneState;
import io.github.ythibos.edi.domain.worker.PermissionMode;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import io.github.ythibos.edi.domain.worker.UsageLimit;
import io.github.ythibos.edi.domain.worker.WindowCheck;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.domain.worker.WorkerWindow;

public interface WorkerRuntime {

    String socket();

    WorkerWindow launch(Launch launch);

    WindowCheck check(WorkerWindow window);

    default boolean isRunning(WorkerWindow window) {
        return check(window).isOpen();
    }

    PaneReading peek(WorkerWindow window);

    void press(WorkerWindow window, Keystroke keystroke);

    void close(WorkerWindow window);

    Optional<UsageLimit> usageLimitStopIn(String transcript, Instant since);

    Optional<Instant> contextLimitStopIn(String transcript, Instant since);

    TokenUsage tokensSpentIn(String transcript, Instant since);

    record Launch(
            WorkerId worker,
            LaunchKind kind,
            UUID session,
            Route route,
            Path worktree,
            Path projectClone,
            Path taskDirectory,
            Path stateDirectory,
            Map<String, String> environment,
            List<String> disallowedTools,
            PermissionMode permissionMode,
            String firstPrompt) {

        public Launch {
            Objects.requireNonNull(worker, "A launch is for a worker.");
            Objects.requireNonNull(kind, () -> worker + " is launched by a dispatch, a resume or a fresh start.");
            Objects.requireNonNull(session, () -> worker + " is launched with the session id EDI chose.");
            Objects.requireNonNull(route, () -> worker + " is launched with an explicit model and effort.");
            Objects.requireNonNull(worktree, () -> worker + " is launched in its isolated copy.");
            Objects.requireNonNull(projectClone, () -> worker + " is launched in a copy of a known clone.");
            environment = Map.copyOf(environment);
            disallowedTools = List.copyOf(disallowedTools);
            Objects.requireNonNull(permissionMode, () -> worker + " is launched with a named permission mode.");
            Objects.requireNonNull(firstPrompt, () -> worker + " is launched with its first prompt.");
        }

        public boolean resumesTheSession() {
            return kind == LaunchKind.RESUME;
        }
    }

    record PaneReading(PaneState state, List<String> lines) {

        public PaneReading {
            Objects.requireNonNull(state, "A pane reading classifies the pane.");
            lines = List.copyOf(lines);
        }
    }
}
