package io.github.ythibos.edi.adapter.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.home.TrackerSettings.Credentials;
import io.github.ythibos.edi.domain.Refusal;

@Tag("adapter")
class KeysFileTest {

    private static final String TOKEN = "token-of-the-keys-file";

    @TempDir
    private Path directory;

    @Test
    void readsTheTokenOfItsSectionAtEveryCall() throws IOException {
        Path keys = Files.writeString(directory.resolve("keys.yml"), "TRACKER:\n  domain: tracker.example.org\n  API-key: " + TOKEN + "\nOTHER:\n  API-key: x\n");

        assertThat(KeysFile.tokenIn(new Credentials(keys, "TRACKER"))).isEqualTo(TOKEN);
        Files.writeString(keys, "TRACKER:\n  API-key: renewed\n");
        assertThat(KeysFile.tokenIn(new Credentials(keys, "TRACKER"))).isEqualTo("renewed");
    }

    @Test
    void refusesAMissingTokenOrFileWithoutShowingTheFilesContent() throws IOException {
        Path keys = Files.writeString(directory.resolve("keys.yml"), "OTHER:\n  API-key: " + TOKEN + "\n");

        assertThatThrownBy(() -> KeysFile.tokenIn(new Credentials(keys, "TRACKER"))).isInstanceOf(Refusal.class)
                .hasMessage("The keys file " + keys + " holds no API-key in its TRACKER section.");
        assertThatThrownBy(() -> KeysFile.tokenIn(new Credentials(directory.resolve("absent.yml"), "TRACKER"))).isInstanceOf(Refusal.class)
                .hasMessageContaining("cannot be read").hasMessageNotContaining(TOKEN);
    }
}
