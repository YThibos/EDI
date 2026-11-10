package io.github.ythibos.edi.adapter.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.port.WorkerRuntime.Login;
import io.github.ythibos.edi.application.port.WorkerRuntime.LoginMismatch;
import io.github.ythibos.edi.domain.worker.LoginName;

@Tag("adapter")
class ClaudeLoginFilesTest {

    @TempDir
    private Path directory;

    @Test
    void findsNoMismatchForATranscriptInTheSessionsFolderOfTheRecordedLogin() {
        ClaudeLoginFiles logins = new ClaudeLoginFiles(directory.resolve("home"));
        Path second = directory.resolve("logins/second");

        assertThat(logins.mismatchOf(transcriptUnder(second), new Login(new LoginName("second"), second))).isEmpty();
        assertThat(logins.mismatchOf(transcriptUnder(directory.resolve("home/.claude")), new Login(LoginName.DEFAULT, null))).isEmpty();
    }

    @Test
    void namesTheDirectoryASessionRanInAndTheOneOfItsRecordedLoginWhenTheyDiffer() {
        ClaudeLoginFiles logins = new ClaudeLoginFiles(directory.resolve("home"));
        Path second = directory.resolve("logins/second");

        assertThat(logins.mismatchOf(transcriptUnder(directory.resolve("home/.claude")), new Login(new LoginName("second"), second)))
                .contains(new LoginMismatch(directory.resolve("home/.claude"), second));
        assertThat(logins.mismatchOf(transcriptUnder(second), new Login(LoginName.DEFAULT, null)))
                .contains(new LoginMismatch(second, directory.resolve("home/.claude")));
    }

    @Test
    void takesALinkToTheRecordedLoginsDirectoryForThatDirectory() throws IOException {
        ClaudeLoginFiles logins = new ClaudeLoginFiles(directory.resolve("home"));
        Path second = Files.createDirectories(directory.resolve("logins/second"));
        Path linked = Files.createSymbolicLink(directory.resolve("second-login"), second);

        assertThat(logins.mismatchOf(transcriptUnder(linked), new Login(new LoginName("second"), second))).isEmpty();
    }

    private static Path transcriptUnder(Path configDirectory) {
        return configDirectory.resolve("projects/-pool-1-lamp").resolve(UUID.randomUUID() + ".jsonl");
    }
}
