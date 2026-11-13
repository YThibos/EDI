package io.github.ythibos.edi.web.primary;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;
import io.github.ythibos.edi.web.PresenterFixtures;

class SessionStartDigestTextTest {

    @TempDir
    private Path directory;

    @Test
    void theMemoryLineNamesTheExpiredAndTheUnticketedWorkarounds() throws IOException {
        TestHome home = TestHome.in(directory).writing("memory/learnings.md", """
                # Learnings

                - Queue the next step before the merge until EDI-69.
                - Book the worklog by hand until EDI-99.
                - Light the harbour lamps by hand until EDI-70.
                """);
        home.storing(List.of(WorkerFixtures.queuedShip("queue-next", "edi", "EDI-69"), WorkerFixtures.queuedShip("light-lamps", "edi", "EDI-70")));
        home.markDone(new TaskId("queue-next"));

        assertThat(memoryLineOf(home)).endsWith(" now. Workarounds in learnings.md: 1 expired and 1 unticketed;"
                + " /stow deletes an expired one and gives an unticketed one a ticket or a date.");
    }

    @Test
    void theMemoryLineSaysNothingOfWorkaroundsWhenNoneIsToCurate() throws IOException {
        TestHome home = TestHome.in(directory).writing("memory/learnings.md", "- Links open with Shift and a click (2026-10-15).\n");

        assertThat(memoryLineOf(home)).endsWith(" now.").doesNotContain("Workarounds");
    }

    @Test
    void saysFirstThatTheAnchorIsMissingAndHowToLoadItWhenTheSessionStartedOutsideTheHome() throws IOException {
        TestHome home = preparedHome();
        Path elsewhere = Files.createDirectories(directory.resolve("elsewhere"));

        String digest = PresenterFixtures.sessionStartDigestText().text(home.sessionStartDigest().digestFor(UUID.randomUUID(), elsewhere));

        assertThat(digest.lines().skip(1).findFirst().orElseThrow()).isEqualTo("The anchor is not in this session: Claude Code loads it, as the"
                + " edi-primary output style the primary's settings name, only for a session started in " + home.home().root() + ". Read "
                + home.home().root().resolve(".claude/output-styles/edi-primary.md") + " now and hold to every line of it, and tell the captain"
                + " this session runs without its anchor until edi up starts the primary again.");
    }

    @Test
    void saysNothingOfTheAnchorWhenTheSessionHoldsIt() throws IOException {
        TestHome home = preparedHome();

        String digest = PresenterFixtures.sessionStartDigestText().text(home.sessionStartDigest().digestFor(UUID.randomUUID(), home.home().root()));

        assertThat(digest).startsWith("EDI session-start digest\nNo session holds the primary role").doesNotContain("anchor");
    }

    private TestHome preparedHome() throws IOException {
        TestHome home = TestHome.in(directory.resolve("home"));
        home.prepareThePrimary(Files.createDirectories(directory.resolve("checkout"))).prepare();
        return home;
    }

    private static String memoryLineOf(TestHome home) {
        String digest = PresenterFixtures.sessionStartDigestText().text(home.sessionStartDigest().digestFor(UUID.randomUUID(), home.home().root()));
        return digest.lines().filter(line -> line.startsWith("Startup memory")).findFirst().orElseThrow();
    }
}
