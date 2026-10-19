package io.github.ythibos.edi.adapter.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("adapter")
class ClaudeSessionsTest {

    @TempDir
    private Path sessions;

    @Test
    void namesAWorkingDirectorysFolderAsClaudeCodeDoes() {
        assertThat(ClaudeSessions.folderOf(Path.of("/home/keeper/.treehouse/lamp-0ef0c1/1/lamp")))
                .isEqualTo("-home-keeper--treehouse-lamp-0ef0c1-1-lamp");
        assertThat(ClaudeSessions.folderOf(Path.of("/home/keeper/.edi/worktrees/lighthouse/lantern_rework")))
                .isEqualTo("-home-keeper--edi-worktrees-lighthouse-lantern-rework");
    }

    @Test
    void findsASessionInAnyFolderAndCarriesItBesideANewWorkingDirectory() throws IOException {
        UUID session = UUID.randomUUID();
        Path transcript = Files.createDirectories(sessions.resolve("-pool-1-lamp")).resolve(session + ".jsonl");
        Files.writeString(transcript, "{\"type\":\"user\"}\n");
        ClaudeSessions claude = new ClaudeSessions(sessions);
        Path copy = Path.of("/home/keeper/.edi/worktrees/lighthouse/lantern-rework");

        claude.carryInto(claude.transcriptOf(session).orElseThrow(), copy);
        claude.carryInto(transcript, copy);

        assertThat(sessions.resolve(ClaudeSessions.folderOf(copy)).resolve(session + ".jsonl")).hasSameTextualContentAs(transcript);
        assertThat(claude.transcriptOf(UUID.randomUUID())).isEmpty();
        assertThat(new ClaudeSessions(sessions.resolve("missing")).transcriptOf(session)).isEmpty();
    }
}
