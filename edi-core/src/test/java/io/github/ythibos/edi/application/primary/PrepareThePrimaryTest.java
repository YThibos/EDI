package io.github.ythibos.edi.application.primary;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.testing.TestHome;
import tools.jackson.databind.json.JsonMapper;

class PrepareThePrimaryTest {

    @TempDir
    private Path directory;

    @Test
    void writesTheSettingsAndTheSystemPromptTheSessionStartsWith() throws IOException {
        Path checkout = Files.createDirectories(directory.resolve("checkout/personal"));
        Files.writeString(checkout.resolve("anchor.local.md"), "Personal addendum for this checkout.\n");
        TestHome home = TestHome.in(directory.resolve("home")).writing("memory/profile.md", "# Profile\n\n- A rule. (2026-10-15)\n");

        PrimaryLaunch launch = home.prepareThePrimary(checkout.getParent()).prepare();

        assertThat(launch.directory()).isEqualTo(home.home().root());
        assertThat(launch.sessionName()).matches("edi-\\d{6}-\\d{4}");
        assertThat(Files.readString(launch.settings())).contains("\"command\": \"edi hook session-start\"");
        assertThat(new JsonMapper().readTree(launch.settings().toFile()).path("statusLine"))
                .hasToString("{\"type\":\"command\",\"command\":\"edi statusline\",\"refreshInterval\":60}");
        assertThat(Files.readString(launch.systemPrompt()))
                .startsWith("# EDI: the primary's anchor")
                .contains("Personal addendum for this checkout.", "# The captain's memory, loaded at session start", "<!-- memory/profile.md -->",
                        "- A rule. (2026-10-15)");
    }
}
