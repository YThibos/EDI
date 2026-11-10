package io.github.ythibos.edi.adapter.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.application.port.WorkerRuntime.Login;
import io.github.ythibos.edi.domain.worker.LoginName;

@Tag("adapter")
class ClaudeSessionsTest {

    @TempDir
    private Path directory;

    @Test
    void namesAWorkingDirectorysFolderAsClaudeCodeDoes() {
        assertThat(ClaudeSessions.folderOf(Path.of("/home/keeper/.treehouse/lamp-0ef0c1/1/lamp")))
                .isEqualTo("-home-keeper--treehouse-lamp-0ef0c1-1-lamp");
        assertThat(ClaudeSessions.folderOf(Path.of("/home/keeper/.edi/worktrees/lighthouse/lantern_rework")))
                .isEqualTo("-home-keeper--edi-worktrees-lighthouse-lantern-rework");
    }

    @Test
    void findsAnAdoptedSessionInAnyFolderOfTheDefaultLoginAndCarriesItBesideANewWorkingDirectory() throws IOException {
        Path sessions = directory.resolve("home/.claude/projects");
        UUID session = UUID.randomUUID();
        Path transcript = Files.createDirectories(sessions.resolve("-pool-1-lamp")).resolve(session + ".jsonl");
        Files.writeString(transcript, "{\"type\":\"user\"}\n");
        ClaudeSessions claude = sessionsWithTheProfile("machine: example\n");
        Path copy = Path.of("/home/keeper/.edi/worktrees/lighthouse/lantern-rework");

        claude.carryInto(claude.transcriptOnTheDefaultLoginOf(session).orElseThrow(), copy);
        claude.carryInto(transcript, copy);

        assertThat(sessions.resolve(ClaudeSessions.folderOf(copy)).resolve(session + ".jsonl")).hasSameTextualContentAs(transcript);
        assertThat(claude.transcriptOnTheDefaultLoginOf(UUID.randomUUID())).isEmpty();
    }

    @Test
    void looksForAnAdoptedSessionInTheDirectoryTheMachineProfileNamesForTheDefaultLoginAndInNoOtherLogins() throws IOException {
        UUID onTheDefault = UUID.randomUUID();
        UUID onAnother = UUID.randomUUID();
        Files.writeString(Files.createDirectories(directory.resolve("logins/main/projects/-pool-1-lamp")).resolve(onTheDefault + ".jsonl"), "{}\n");
        Files.writeString(Files.createDirectories(directory.resolve("logins/hobby/projects/-pool-1-lamp")).resolve(onAnother + ".jsonl"), "{}\n");
        ClaudeSessions claude = sessionsWithTheProfile("claude:\n  logins:\n    default: { configDirectory: %s }\n    hobby: { configDirectory: %s }\n"
                .formatted(directory.resolve("logins/main"), directory.resolve("logins/hobby")));

        assertThat(claude.transcriptOnTheDefaultLoginOf(onTheDefault)).isPresent();
        assertThat(claude.transcriptOnTheDefaultLoginOf(onAnother)).isEmpty();
        assertThat(sessionsWithTheProfile("machine: example\n").transcriptOnTheDefaultLoginOf(onTheDefault)).isEmpty();
    }

    @Test
    void looksForASessionInTheSessionsFolderOfTheLoginItIsAskedAboutOnly() throws IOException {
        UUID onTheFirst = UUID.randomUUID();
        UUID onTheSecond = UUID.randomUUID();
        Files.writeString(Files.createDirectories(directory.resolve("logins/first/projects/-pool-1-lamp")).resolve(onTheFirst + ".jsonl"), "{}\n");
        Files.writeString(Files.createDirectories(directory.resolve("logins/second/projects/-pool-1-lamp")).resolve(onTheSecond + ".jsonl"), "{}\n");
        Files.writeString(Files.createDirectories(directory.resolve("home/.claude/projects/-pool-1-lamp")).resolve(onTheFirst + ".jsonl"), "{}\n");
        ClaudeSessions claude = sessionsWithTheProfile("machine: example\n");
        Login first = new Login(new LoginName("first"), directory.resolve("logins/first"));
        Login second = new Login(new LoginName("second"), directory.resolve("logins/second"));

        assertThat(claude.transcriptOf(onTheFirst, first)).isPresent();
        assertThat(claude.transcriptOf(onTheSecond, first)).isEmpty();
        assertThat(claude.transcriptOf(onTheSecond, second)).isPresent();
        assertThat(claude.transcriptOf(onTheFirst, second)).isEmpty();
        assertThat(claude.transcriptOf(onTheFirst, new Login(LoginName.DEFAULT, null))).isPresent();
        assertThat(claude.transcriptOf(onTheSecond, new Login(LoginName.DEFAULT, null))).isEmpty();
    }

    private ClaudeSessions sessionsWithTheProfile(String profile) throws IOException {
        EdiHome home = new EdiHome(directory.resolve("edi"));
        Files.createDirectories(home.machineProfile().getParent());
        Files.writeString(home.machineProfile(), profile);
        return new ClaudeSessions(new ClaudeLoginFiles(directory.resolve("home")), new MachineProfile(home));
    }
}
