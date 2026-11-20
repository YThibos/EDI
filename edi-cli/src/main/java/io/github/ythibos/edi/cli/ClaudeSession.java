package io.github.ythibos.edi.cli;

import java.util.Map;
import java.util.Optional;

record ClaudeSession(String id, String pid, String projectDirectory, String primaryLaunchKey) {

    static final String PRIMARY_LAUNCH_KEY = "EDI_PRIMARY_LAUNCH_KEY";

    static ClaudeSession ofEnvironment(Map<String, String> environment) {
        return new ClaudeSession(present(environment.get("CLAUDE_CODE_SESSION_ID")), present(environment.get("CLAUDE_PID")),
                present(environment.get("CLAUDE_PROJECT_DIR")), present(environment.get(PRIMARY_LAUNCH_KEY)));
    }

    Optional<String> idIfAny() {
        return Optional.ofNullable(id);
    }

    Optional<String> pidIfAny() {
        return Optional.ofNullable(pid);
    }

    Optional<String> projectDirectoryIfAny() {
        return Optional.ofNullable(projectDirectory);
    }

    Optional<String> primaryLaunchKeyIfAny() {
        return Optional.ofNullable(primaryLaunchKey);
    }

    boolean runsWithoutTheLaunchKey() {
        return id != null && primaryLaunchKey == null;
    }

    private static String present(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
