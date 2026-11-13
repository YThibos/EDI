package io.github.ythibos.edi.web.primary;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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
    void offersTheWeeklyGardeningRoundOnMondayFromTheWeeklyUsageResetOn() throws IOException {
        TestHome home = TestHome.in(directory);
        home.clock().advance(Duration.between(TestHome.START, Instant.parse("2026-10-19T08:59:00Z")));

        assertThat(digestOf(home)).doesNotContain("gardening");
        home.clock().advance(Duration.ofMinutes(1));
        assertThat(digestOf(home)).contains("\nIt is Monday after the weekly usage reset: offer the captain the weekly gardening round,"
                + " the upkeep check included (gardening).\n");
    }

    private static String memoryLineOf(TestHome home) {
        return digestOf(home).lines().filter(line -> line.startsWith("Startup memory")).findFirst().orElseThrow();
    }

    private static String digestOf(TestHome home) {
        return PresenterFixtures.sessionStartDigestText().text(home.sessionStartDigest().digestFor(UUID.randomUUID()));
    }
}
