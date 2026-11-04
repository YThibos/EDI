package io.github.ythibos.edi.adapter.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class WorkerSettingsFile {

    private static final String FILE_NAME = "claude-settings.json";
    private static final String BARE_EDI = "edi ";
    private static final String COMMAND = "command";

    private WorkerSettingsFile() {
    }

    static Path writtenInto(Path stateDirectory, Path edi, JsonMapper json) {
        try {
            Files.createDirectories(stateDirectory);
            Path settings = stateDirectory.resolve(FILE_NAME);
            Files.writeString(settings, json.writeValueAsString(withHooksCalling(edi, json.readTree(ClaudeLayer.read(ClaudeLayer.WORKER_SETTINGS)))),
                    StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(settings, PosixFilePermissions.fromString("rw-------"));
            return settings;
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private static JsonNode withHooksCalling(Path edi, JsonNode settings) {
        for (JsonNode event : settings.path("hooks").values()) {
            for (JsonNode matcher : event.values()) {
                for (JsonNode hook : matcher.path("hooks").values()) {
                    String command = hook.path(COMMAND).asString("");
                    if (command.startsWith(BARE_EDI)) {
                        ((ObjectNode) hook).put(COMMAND, quoted(edi.toString()) + command.substring(BARE_EDI.length() - 1));
                    }
                }
            }
        }
        return settings;
    }

    private static String quoted(String word) {
        return "'" + word.replace("'", "'\\''") + "'";
    }
}
