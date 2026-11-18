package io.github.ythibos.edi.adapter.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.EdiHome;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Tag("adapter")
class PipelineGuardTest {

    private static final JsonMapper JSON = new JsonMapper();

    @TempDir
    private Path directory;

    @Test
    void guardsThePipelinesAgentWithThePreToolHookUnderTheHomeItNames() throws IOException {
        EdiHome home = new EdiHome(directory.resolve("edi home"));

        Path settings = PipelineGuard.settingsWrittenFor(home, JSON);
        JsonNode guard = JSON.readTree(Files.readString(settings)).path("hooks").path("PreToolUse").get(0);

        assertThat(settings).isEqualTo(home.root().resolve("state/pipeline/settings.json"));
        assertThat(guard.path("matcher").asString()).isEqualTo("Bash|Monitor|Read");
        assertThat(guard.path("hooks").get(0).path("command").asString()).isEqualTo("EDI_HOME='" + home.root() + "' edi hook pre-tool-use");
    }

    @Test
    void quotesAHomeThatHoldsAQuote() throws IOException {
        EdiHome home = new EdiHome(directory.resolve("the captain's home"));

        Path settings = PipelineGuard.settingsWrittenFor(home, JSON);

        assertThat(JSON.readTree(Files.readString(settings)).path("hooks").path("PreToolUse").get(0).path("hooks").get(0).path("command").asString())
                .isEqualTo("EDI_HOME='" + directory + "/the captain'\\''s home' edi hook pre-tool-use");
    }
}
