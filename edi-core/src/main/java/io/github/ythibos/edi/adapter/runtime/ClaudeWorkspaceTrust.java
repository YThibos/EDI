package io.github.ythibos.edi.adapter.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class ClaudeWorkspaceTrust {

    private static final String CONFIG_FILE = ".claude.json";
    private static final String PROJECTS = "projects";
    private static final String TRUST_ACCEPTED = "hasTrustDialogAccepted";

    private final Path configFile;
    private final JsonMapper json;

    public ClaudeWorkspaceTrust(@Value("${edi.workers.claude-config}") Path configDirectory, JsonMapper json) {
        this.configFile = configDirectory.resolve(CONFIG_FILE);
        this.json = json;
    }

    public synchronized void trust(Path directory) {
        if (!Files.isRegularFile(configFile)) {
            return;
        }
        ObjectNode config = (ObjectNode) json.readTree(contentOf(configFile));
        String key = directory.toAbsolutePath().normalize().toString();
        if (config.path(PROJECTS).path(key).path(TRUST_ACCEPTED).asBoolean(false)) {
            return;
        }
        projectEntry(config, key).put(TRUST_ACCEPTED, true);
        replace(configFile, json.writerWithDefaultPrettyPrinter().writeValueAsString(config));
    }

    private static ObjectNode projectEntry(ObjectNode config, String key) {
        JsonNode projects = config.path(PROJECTS);
        ObjectNode entries = projects.isObject() ? (ObjectNode) projects : config.putObject(PROJECTS);
        JsonNode entry = entries.path(key);
        return entry.isObject() ? (ObjectNode) entry : entries.putObject(key);
    }

    private static String contentOf(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static void replace(Path file, String content) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
            Path written = Files.createTempFile(file.getParent(), CONFIG_FILE + "-", ".tmp");
            Files.writeString(written, content, StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(written, permissions);
            Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }
}
