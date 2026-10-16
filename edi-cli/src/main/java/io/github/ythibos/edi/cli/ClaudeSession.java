package io.github.ythibos.edi.cli;

import java.util.Map;
import java.util.Optional;

record ClaudeSession(String id, String pid) {

    static ClaudeSession ofEnvironment(Map<String, String> environment) {
        return new ClaudeSession(present(environment.get("CLAUDE_CODE_SESSION_ID")), present(environment.get("CLAUDE_PID")));
    }

    Optional<String> idIfAny() {
        return Optional.ofNullable(id);
    }

    Optional<String> pidIfAny() {
        return Optional.ofNullable(pid);
    }

    private static String present(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
