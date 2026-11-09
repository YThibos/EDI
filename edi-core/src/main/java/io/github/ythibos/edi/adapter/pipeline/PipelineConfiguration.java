package io.github.ythibos.edi.adapter.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import io.github.ythibos.edi.adapter.home.ConfigurationYaml;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.rule.Rule;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

final class PipelineConfiguration {

    private static final Pattern NESTED_BLANK_OR_COMMENT = Pattern.compile("^(\\s.*|#.*|)$");
    private static final Pattern BLANK_OR_COMMENT = Pattern.compile("^\\s*(#.*)?$");
    private static final YAMLMapper YAML = ConfigurationYaml.writer();

    private PipelineConfiguration() {
    }

    static void changeEntry(Path globalConfiguration, String key, Consumer<ObjectNode> change) {
        if (!Files.isRegularFile(globalConfiguration)) {
            return;
        }
        try {
            changeEntryIn(globalConfiguration, key, change);
        } catch (IOException unwritable) {
            throw new Refusal(Rule.CONFIGURATION_VALID, "The pipeline configuration " + globalConfiguration + " cannot be read or replaced: " + unwritable.getClass().getSimpleName() + ".");
        } catch (JacksonException malformed) {
            throw new Refusal(Rule.CONFIGURATION_VALID, "The pipeline configuration " + globalConfiguration + " is not readable YAML: " + malformed.getOriginalMessage());
        }
    }

    private static void changeEntryIn(Path globalConfiguration, String key, Consumer<ObjectNode> change) throws IOException {
        String configuration = Files.readString(globalConfiguration, StandardCharsets.UTF_8);
        JsonNode existing = YAML.readTree(configuration).path(key);
        ObjectNode changed = existing.isObject() ? ((ObjectNode) existing).deepCopy() : YAML.createObjectNode();
        change.accept(changed);
        if (!changed.equals(existing)) {
            replace(globalConfiguration, withEntryReplaced(configuration.lines().toList(), key, changed));
        }
    }

    private static String withEntryReplaced(List<String> lines, String key, ObjectNode value) {
        String entry = YAML.writeValueAsString(YAML.createObjectNode().set(key, value));
        int start = indexOfTheEntry(lines, key);
        if (start < 0) {
            return joined(lines) + (lines.isEmpty() ? "" : "\n") + entry;
        }
        int end = endOfTheEntry(lines, start);
        return joined(lines.subList(0, start)) + entry + joined(lines.subList(end, lines.size()));
    }

    private static int indexOfTheEntry(List<String> lines, String key) {
        Pattern entryKey = Pattern.compile("^" + Pattern.quote(key) + ":.*");
        for (int index = 0; index < lines.size(); index++) {
            if (entryKey.matcher(lines.get(index)).matches()) {
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

    private static void replace(Path file, String content) throws IOException {
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
        Path written = Files.createTempFile(file.getParent(), file.getFileName() + "-", ".tmp");
        try {
            Files.writeString(written, content, StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(written, permissions);
            Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(written);
        }
    }
}
