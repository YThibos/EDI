package io.github.ythibos.edi.adapter.runtime;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import io.github.ythibos.edi.application.port.WorkerRuntime.Launch;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.worker.PermissionMode;

final class ClaudeCommandLine {

    private static final String CLAUDE_CODE_SESSION_VARIABLES = "CLAUDE";
    private static final String CLAUDE_CODE_CONFIGURATION = "CLAUDE_CONFIG_DIR";

    private ClaudeCommandLine() {
    }

    static List<String> of(String claudeCode, Launch launch, Path settings, String systemPrompt, Set<String> inheritedVariables) {
        List<String> words = new ArrayList<>(withoutTheLaunchingSessionsVariables(inheritedVariables));
        words.add(claudeCode);
        if (!launch.disallowedTools().isEmpty()) {
            words.add("--disallowedTools");
            words.addAll(launch.disallowedTools());
        }
        words.addAll(List.of("--add-dir", launch.taskDirectory().toString()));
        words.addAll(List.of("--session-id", launch.session().toString()));
        words.addAll(modelAndEffort(launch.route()));
        words.addAll(List.of("--name", launch.worker().task().value()));
        words.addAll(List.of("--settings", settings.toString()));
        words.addAll(permissionFlags(launch.permissionMode()));
        words.addAll(List.of("--append-system-prompt", systemPrompt));
        words.addAll(List.of("--", launch.firstPrompt()));
        return words;
    }

    private static List<String> withoutTheLaunchingSessionsVariables(Set<String> inheritedVariables) {
        List<String> unset = inheritedVariables.stream()
                .filter(variable -> variable.startsWith(CLAUDE_CODE_SESSION_VARIABLES) && !variable.equals(CLAUDE_CODE_CONFIGURATION))
                .sorted()
                .flatMap(variable -> Stream.of("-u", variable))
                .toList();
        return unset.isEmpty() ? List.of() : Stream.concat(Stream.of("env"), unset.stream()).toList();
    }

    private static List<String> modelAndEffort(Route route) {
        List<String> words = new ArrayList<>(List.of("--model", route.model().value()));
        if (route.namesAnEffort()) {
            words.addAll(List.of("--effort", route.effort().word()));
        }
        return words;
    }

    private static List<String> permissionFlags(PermissionMode mode) {
        return switch (mode) {
            case BYPASS -> List.of("--dangerously-skip-permissions");
            case AUTO -> List.of("--permission-mode", "auto");
            case PROMPT -> List.of("--permission-mode", "default");
        };
    }
}
