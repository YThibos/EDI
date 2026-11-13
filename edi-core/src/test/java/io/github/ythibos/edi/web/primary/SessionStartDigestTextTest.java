package io.github.ythibos.edi.web.primary;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.primary.SessionSource;
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

        assertThat(digest.lines().skip(1).limit(2)).containsExactly("The anchor is not in this session: Claude Code loads it, as the"
                + " edi-primary output style the primary's settings name, only for a session started in " + home.home().root() + ". Read "
                + home.home().root().resolve(".claude/output-styles/edi-primary.md") + " now and hold to every line of it, and tell the captain"
                + " this session runs without its anchor until edi up starts the primary again.", confirmationOfTheAnchorIn(home));
    }

    @Test
    void saysFirstThatTheSessionHoldsAnOlderAnchorAndPointsAtTheCheckoutsCurrentOne() throws IOException {
        TestHome home = TestHome.in(directory.resolve("home"))
                .writing(".claude/output-styles/edi-primary.md", "---\nname: edi-primary\n---\n\n# EDI: the primary's anchor\n\nYou are an older E.D.I.\n")
                .writing("checkout/claude/primary/CLAUDE.md", "# EDI: the primary's anchor\n\nYou are E.D.I.\n");

        String digest = PresenterFixtures.sessionStartDigestText().text(home.sessionStartDigest().digestFor(UUID.randomUUID(), home.home().root()));

        assertThat(digest.lines().skip(1).limit(2)).containsExactly("This session holds an older anchor than the core's: the edi-primary output style at "
                + home.home().root().resolve(".claude/output-styles/edi-primary.md") + " predates the core's build, and a compaction or a new start loads"
                + " that older anchor again until edi up or edi update refreshes it. Read " + home.home().root().resolve("checkout/claude/primary/CLAUDE.md")
                + " now and hold to every line of it, for its rules replace the older ones.", confirmationOfTheAnchorIn(home));
    }

    @Test
    void asksForEdiUpWhenTheSessionHoldsAnOlderAnchorAndNoCheckoutHoldsTheCurrentOne() throws IOException {
        TestHome home = TestHome.in(directory.resolve("home"))
                .writing(".claude/output-styles/edi-primary.md", "---\nname: edi-primary\n---\n\n# EDI: the primary's anchor\n\nYou are an older E.D.I.\n");

        String digest = PresenterFixtures.sessionStartDigestText().text(home.sessionStartDigest().digestFor(UUID.randomUUID(), home.home().root()));

        assertThat(digest.lines().skip(1).findFirst().orElseThrow()).startsWith("This session holds an older anchor than the core's: ")
                .endsWith(" The core finds no checkout of its anchor to read, so tell the captain this session runs on an older anchor and ask for edi up,"
                        + " which writes the current one into the output style.");
    }

    @Test
    void asksOnlyForTheSessionsOwnConfirmationOfTheAnchorWhenTheSessionHoldsIt() throws IOException {
        TestHome home = preparedHome();

        String digest = PresenterFixtures.sessionStartDigestText().text(home.sessionStartDigest().digestFor(UUID.randomUUID(), home.home().root()));

        assertThat(digest).startsWith("EDI session-start digest\n" + confirmationOfTheAnchorIn(home) + "\nNo session holds the primary role")
                .doesNotContain("The anchor is not in this session");
    }

    @Test
    void asksAStandbySessionForTheSameConfirmation() throws IOException {
        TestHome home = preparedHome();
        UUID primary = UUID.randomUUID();
        UUID standby = UUID.randomUUID();
        home.primaryHooks().sessionStarted(primary, null, SessionSource.LAUNCH);
        home.primaryHooks().promptSubmitted(primary, "Status?");
        home.primaryHooks().sessionStarted(standby, null, SessionSource.LAUNCH);

        String digest = PresenterFixtures.sessionStartDigestText().text(home.sessionStartDigest().digestFor(standby, home.home().root()));

        assertThat(digest).startsWith("EDI session-start digest\n" + confirmationOfTheAnchorIn(home) + "\nThis session is standby");
    }

    private static String confirmationOfTheAnchorIn(TestHome home) {
        return "Confirm that this session's system prompt holds the heading \"# EDI: the primary's anchor\"; if it does not, read "
                + home.home().root().resolve(".claude/output-styles/edi-primary.md")
                + " at once and tell the captain this session runs without its anchor, for a primary never works unaware.";
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
