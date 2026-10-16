package io.github.ythibos.edi.adapter.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.worker.TokenUsage;
import io.github.ythibos.edi.domain.worker.UsageLimit;
import tools.jackson.databind.json.JsonMapper;

class ClaudeTranscriptTest {

    private static final Instant LAUNCHED = Instant.parse("2026-10-16T09:00:00Z");
    private static final Instant RESET = Instant.ofEpochSecond(1792160400);

    @TempDir
    private Path directory;

    private final ClaudeTranscript transcripts = new ClaudeTranscript(new JsonMapper());

    @Test
    void readsTheUsageLimitASessionStoppedOnAndWhenItResets() throws IOException {
        assertThat(transcripts.usageLimitStop(fixture("session-limit.jsonl"), LAUNCHED)).contains(new UsageLimit("session limit", RESET));
        assertThat(transcripts.usageLimitStop(fixture("session-limit-error-params.jsonl"), LAUNCHED)).contains(new UsageLimit("session limit", RESET));
        assertThat(transcripts.usageLimitStop(fixture("weekly-limit.jsonl"), LAUNCHED))
                .contains(new UsageLimit("weekly limit", Instant.ofEpochSecond(1792360800)));
        assertThat(transcripts.usageLimitStop(fixture("spend-limit.jsonl"), LAUNCHED)).contains(new UsageLimit("monthly spend limit", null));
    }

    @Test
    void findsNoLimitStopWhereTheSessionDidNotStopOnOne() throws IOException {
        for (String fixture : new String[] {"working.jsonl", "overloaded.jsonl", "quoted-limit.jsonl", "limit-without-timestamp.jsonl", "truncated.jsonl"}) {
            assertThat(transcripts.usageLimitStop(fixture(fixture), LAUNCHED)).as(fixture).isEmpty();
        }
        assertThat(transcripts.usageLimitStop(directory.resolve("absent.jsonl"), LAUNCHED)).isEmpty();
    }

    @Test
    void ignoresALimitStopWrittenBeforeTheSessionWasLaunchedAgain() throws IOException {
        assertThat(transcripts.usageLimitStop(fixture("session-limit.jsonl"), Instant.parse("2026-10-16T10:15:00.001Z"))).isEmpty();
        assertThat(transcripts.usageLimitStop(fixture("session-limit.jsonl"), Instant.parse("2026-10-16T10:15:00Z"))).isPresent();
    }

    @Test
    void readsOnlyTheTailOfALongTranscript() throws IOException {
        Path transcript = directory.resolve("long.jsonl");
        String filler = "{\"type\":\"assistant\",\"message\":{\"id\":\"filler\",\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"" + "x".repeat(1_000) + "\"}]},"
                + "\"timestamp\":\"2026-10-16T09:30:00.000Z\"}\n";
        Files.writeString(transcript, filler.repeat(400) + Files.readString(fixture("session-limit.jsonl")));

        assertThat(Files.size(transcript)).isGreaterThan(256 * 1024);
        assertThat(transcripts.usageLimitStop(transcript, LAUNCHED)).contains(new UsageLimit("session limit", RESET));
    }

    @Test
    void countsEachMessagesUsageOnceAndOnlySinceTheLaunch() throws IOException {
        Path usage = fixture("usage.jsonl");

        assertThat(transcripts.tokensSpent(usage, Instant.parse("2026-10-16T10:00:00Z"))).isEqualTo(new TokenUsage(3, 8300, 320, 55));
        assertThat(transcripts.tokensSpent(usage, LAUNCHED)).isEqualTo(new TokenUsage(1003, 9300, 1320, 1055));
        assertThat(transcripts.tokensSpent(directory.resolve("absent.jsonl"), LAUNCHED)).isEqualTo(TokenUsage.NONE);
    }

    private Path fixture(String name) throws IOException {
        Path copy = directory.resolve(name);
        try (InputStream fixture = ClaudeTranscriptTest.class.getResourceAsStream("/claude-transcript/" + name)) {
            Files.write(copy, fixture.readAllBytes());
        }
        return copy;
    }
}
