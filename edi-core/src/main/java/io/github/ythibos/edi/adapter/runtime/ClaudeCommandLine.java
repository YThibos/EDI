package io.github.ythibos.edi.adapter.runtime;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import io.github.ythibos.edi.application.port.WorkerRuntime.Launch;
import io.github.ythibos.edi.application.port.WorkerRuntime.Login;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.worker.PermissionMode;

final class ClaudeCommandLine {

    static final String CONFIG_DIRECTORY = "CLAUDE_CONFIG_DIR";

    private static final String CLAUDE_CODE_SESSION_VARIABLES = "CLAUDE";
    private static final List<String> GIT_COMMAND_SCOPE_SWITCHES = List.of("GIT_CONFIG_COUNT", "GIT_CONFIG_PARAMETERS");
    private static final List<String> GIT_COMMAND_SCOPE_ENTRIES = List.of("GIT_CONFIG_KEY_", "GIT_CONFIG_VALUE_");
    private static final String NO_TOOL_INSTALLS_AT_LAUNCH = "MISE_EXEC_AUTO_INSTALL=false";

    private ClaudeCommandLine() {
    }

    static List<String> of(String claudeCode, String mise, boolean cloneTrustedByMise, Launch launch, Path settings, String systemPrompt,
            Set<String> inheritedVariables) {
        List<String> words = new ArrayList<>(withoutInheritedOverrides(inheritedVariables, launch.login()));
        words.addAll(onTheCopysPinnedToolchain(mise, cloneTrustedByMise));
        words.add(claudeCode);
        if (!launch.disallowedTools().isEmpty()) {
            words.add("--disallowedTools");
            words.addAll(launch.disallowedTools());
        }
        words.addAll(List.of("--add-dir", launch.taskDirectory().toString()));
        words.addAll(List.of(launch.resumesTheSession() ? "--resume" : "--session-id", launch.session().toString()));
        words.addAll(modelAndEffort(launch.route()));
        words.addAll(List.of("--name", launch.worker().task().value()));
        words.addAll(List.of("--settings", settings.toString()));
        words.addAll(permissionFlags(launch.permissionMode()));
        words.addAll(List.of("--append-system-prompt", systemPrompt));
        words.addAll(List.of("--", launch.firstPrompt()));
        return words;
    }

    static Map<String, String> windowEnvironment(Launch launch) {
        Map<String, String> environment = new HashMap<>(launch.environment());
        if (!launch.login().keepsClaudeCodesOwnDirectory()) {
            environment.put(CONFIG_DIRECTORY, launch.login().configDirectory().toString());
        }
        return environment;
    }

    private static List<String> withoutInheritedOverrides(Set<String> inheritedVariables, Login login) {
        Stream<String> overrides = inheritedVariables.stream().filter(variable -> belongsToTheLaunchingSession(variable) || isAGitConfigurationEntry(variable));
        Stream<String> unset = Stream.of(GIT_COMMAND_SCOPE_SWITCHES.stream(), overrides, claudeCodesOwnDirectoryFor(login)).flatMap(variables -> variables)
                .distinct().sorted();
        return Stream.concat(Stream.of("env"), unset.flatMap(variable -> Stream.of("-u", variable))).toList();
    }

    private static boolean belongsToTheLaunchingSession(String variable) {
        return variable.startsWith(CLAUDE_CODE_SESSION_VARIABLES) && !variable.equals(CONFIG_DIRECTORY);
    }

    private static boolean isAGitConfigurationEntry(String variable) {
        return GIT_COMMAND_SCOPE_ENTRIES.stream().anyMatch(variable::startsWith);
    }

    private static Stream<String> claudeCodesOwnDirectoryFor(Login login) {
        return login.keepsClaudeCodesOwnDirectory() ? Stream.of(CONFIG_DIRECTORY) : Stream.empty();
    }

    private static List<String> onTheCopysPinnedToolchain(String mise, boolean cloneTrustedByMise) {
        return cloneTrustedByMise ? List.of(NO_TOOL_INSTALLS_AT_LAUNCH, mise, "exec", "--") : List.of();
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
