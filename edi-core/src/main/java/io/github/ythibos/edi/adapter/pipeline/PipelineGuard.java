package io.github.ythibos.edi.adapter.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.ythibos.edi.EdiHome;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class PipelineGuard {

    private static final String GUARDED_TOOLS = "Bash|Monitor|Read";

    private PipelineGuard() {
    }

    static Path settingsWrittenFor(EdiHome home, JsonMapper json) {
        Path settings = home.pipelineAgentSettings();
        try {
            Files.createDirectories(settings.getParent());
            Files.writeString(settings, json.writerWithDefaultPrettyPrinter().writeValueAsString(guardedBy(home, json)) + "\n", StandardCharsets.UTF_8);
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
}
