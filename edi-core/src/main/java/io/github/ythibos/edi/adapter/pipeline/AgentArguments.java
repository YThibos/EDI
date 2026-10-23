package io.github.ythibos.edi.adapter.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.ythibos.edi.adapter.home.ConfigurationYaml;
import io.github.ythibos.edi.domain.dispatch.Route;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

final class AgentArguments {

    private static final String AGENT_ARGUMENTS = "agent_args_override";
    private static final String CLAUDE_CODE = "claude";
    private static final Pattern AGENT_ARGUMENTS_KEY = Pattern.compile("^" + AGENT_ARGUMENTS + ":.*");
    private static final Pattern NESTED_BLANK_OR_COMMENT = Pattern.compile("^(\\s.*|#.*|)$");
    private static final Pattern BLANK_OR_COMMENT = Pattern.compile("^\\s*(#.*)?$");
    private static final YAMLMapper YAML = ConfigurationYaml.writer();

    private AgentArguments() {
    }

    static void routeClaudeCodeIn(Path globalConfiguration, Route route) {
        if (!Files.isRegularFile(globalConfiguration)) {
            return;
        }
        String configuration = contentOf(globalConfiguration);
        List<String> arguments = argumentsFor(route);
        if (!claudeCodeArgumentsIn(configuration).equals(arguments)) {
            replace(globalConfiguration, withClaudeCodeArguments(configuration, arguments));
        }
    }

    private static String contentOf(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static List<String> argumentsFor(Route route) {
        List<String> arguments = new ArrayList<>(List.of("--model", route.model().value()));
        if (route.namesAnEffort()) {
            arguments.addAll(List.of("--effort", route.effort().word()));
        }
        return arguments;
    }

    private static List<String> claudeCodeArgumentsIn(String configuration) {
        return agentArgumentsIn(configuration).path(CLAUDE_CODE).valueStream().map(argument -> argument.asString("")).toList();
    }

    private static JsonNode agentArgumentsIn(String configuration) {
        return YAML.readTree(configuration).path(AGENT_ARGUMENTS);
    }

    private static String withClaudeCodeArguments(String configuration, List<String> arguments) {
        JsonNode existing = agentArgumentsIn(configuration);
        ObjectNode agents = existing.isObject() ? (ObjectNode) existing : YAML.createObjectNode();
        agents.set(CLAUDE_CODE, YAML.valueToTree(arguments));
        String entry = YAML.writeValueAsString(YAML.createObjectNode().set(AGENT_ARGUMENTS, agents));
        return withEntryReplaced(configuration.lines().toList(), entry);
    }

    private static String withEntryReplaced(List<String> lines, String entry) {
        int start = indexOfTheEntry(lines);
        if (start < 0) {
            return joined(lines) + (lines.isEmpty() ? "" : "\n") + entry;
        }
        int end = endOfTheEntry(lines, start);
        return joined(lines.subList(0, start)) + entry + joined(lines.subList(end, lines.size()));
    }

    private static int indexOfTheEntry(List<String> lines) {
        for (int index = 0; index < lines.size(); index++) {
            if (AGENT_ARGUMENTS_KEY.matcher(lines.get(index)).matches()) {
                return index;
            }
        }
        return -1;
    }

    private static int endOfTheEntry(List<String> lines, int start) {
        int end = start + 1;
        while (end < lines.size() && NESTED_BLANK_OR_COMMENT.matcher(lines.get(end)).matches()) {
            end++;
        }
        while (end > start + 1 && BLANK_OR_COMMENT.matcher(lines.get(end - 1)).matches()) {
            end--;
        }
        return end;
    }

    private static String joined(List<String> lines) {
        return lines.stream().map(line -> line + "\n").reduce("", String::concat);
    }

    private static void replace(Path file, String content) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
            Path written = Files.createTempFile(file.getParent(), file.getFileName() + "-", ".tmp");
            Files.writeString(written, content, StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(written, permissions);
            Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }
}
