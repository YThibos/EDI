package io.github.ythibos.edi.adapter.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import io.github.ythibos.edi.EdiHome;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class PipelineGuard {

    private static final String GUARDED_TOOLS = "Bash|Monitor|Read";

    private PipelineGuard() {
    }

    static Path settingsWrittenFor(EdiHome home, JsonMapper json) {
        Path settings = home.pipelineAgentSettings();
        String guard = json.writerWithDefaultPrettyPrinter().writeValueAsString(guardedBy(home, json)) + "\n";
        try {
            if (!holds(settings, guard)) {
                replace(settings, guard);
            }
            return settings;
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private static ObjectNode guardedBy(EdiHome home, JsonMapper json) {
        ObjectNode settings = json.createObjectNode();
        ObjectNode matcher = settings.putObject("hooks").putArray("PreToolUse").addObject().put("matcher", GUARDED_TOOLS);
        matcher.putArray("hooks").addObject().put("type", "command").put("command", guardCommandIn(home));
        return settings;
    }

    private static String guardCommandIn(EdiHome home) {
        return "EDI_HOME=" + shellQuoted(home.root().toString()) + " edi hook pre-tool-use";
    }

    private static String shellQuoted(String word) {
        return "'" + word.replace("'", "'\\''") + "'";
    }

    private static boolean holds(Path settings, String guard) throws IOException {
        return Files.isRegularFile(settings) && Files.readString(settings, StandardCharsets.UTF_8).equals(guard);
    }

    private static void replace(Path settings, String guard) throws IOException {
        Files.createDirectories(settings.getParent());
        Path written = Files.createTempFile(settings.getParent(), settings.getFileName() + "-", ".tmp");
        try {
            Files.writeString(written, guard, StandardCharsets.UTF_8);
            Files.move(written, settings, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(written);
        }
    }
}
