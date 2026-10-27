package io.github.ythibos.edi.adapter.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.worker.ErrorStop;
import io.github.ythibos.edi.domain.worker.LimitStop;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import io.github.ythibos.edi.domain.worker.UsageLimit;
import tools.jackson.databind.json.JsonMapper;

class ClaudeTranscriptTest {

    private static final Instant LAUNCHED = Instant.parse("2026-10-16T09:00:00Z");
    private static final Instant RESET = Instant.ofEpochSecond(1792160400);
    private static final Instant LIMIT_HIT = Instant.parse("2026-10-16T10:15:00Z");

    @TempDir
    private Path directory;

    private final ClaudeTranscript transcripts = new ClaudeTranscript(new JsonMapper());

    @Test
    void readsTheUsageLimitASessionStoppedOnWhenItStoppedAndWhenItResets() throws IOException {
        assertThat(transcripts.usageLimitStop(fixture("session-limit.jsonl"), LAUNCHED))
                .contains(new LimitStop(LIMIT_HIT, new UsageLimit("session limit", RESET)));
        assertThat(transcripts.usageLimitStop(fixture("session-limit-error-params.jsonl"), LAUNCHED))
                .contains(new LimitStop(LIMIT_HIT, new UsageLimit("session limit", RESET)));
        assertThat(transcripts.usageLimitStop(fixture("weekly-limit.jsonl"), LAUNCHED))
                .contains(new LimitStop(Instant.parse("2026-10-16T11:00:00Z"), new UsageLimit("weekly limit", Instant.ofEpochSecond(1792360800))));
        assertThat(transcripts.usageLimitStop(fixture("spend-limit.jsonl"), LAUNCHED))
                .contains(new LimitStop(Instant.parse("2026-10-16T11:00:00Z"), new UsageLimit("monthly spend limit", null)));
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
    void readsWhenASessionStoppedOnItsFullContext() throws IOException {
        Instant full = Instant.parse("2026-10-16T10:15:00Z");

        assertThat(transcripts.contextLimitStop(fixture("context-full.jsonl"), LAUNCHED)).contains(full);
        assertThat(transcripts.contextLimitStop(fixture("context-full.jsonl"), full.plusMillis(1))).isEmpty();
        for (String fixture : new String[] {"working.jsonl", "overloaded.jsonl", "session-limit.jsonl", "quoted-context-limit.jsonl", "truncated.jsonl"}) {
            assertThat(transcripts.contextLimitStop(fixture(fixture), LAUNCHED)).as(fixture).isEmpty();
        }
        assertThat(transcripts.contextLimitStop(directory.resolve("absent.jsonl"), LAUNCHED)).isEmpty();
    }

    @Test
    void readsWhenATurnEndedAfterAStopHookEdiCouldNotTake() throws IOException {
        Instant ended = Instant.parse("2026-10-16T10:15:00.080Z");

        assertThat(transcripts.missedStop(fixture("missed-stop.jsonl"), LAUNCHED)).contains(ended);
        assertThat(transcripts.missedStop(fixture("missed-stop.jsonl"), ended.plusMillis(1))).isEmpty();
        for (String fixture : new String[] {"answered-stop.jsonl", "blocked-stop.jsonl", "context-full.jsonl", "working.jsonl", "truncated.jsonl"}) {
            assertThat(transcripts.missedStop(fixture(fixture), LAUNCHED)).as(fixture).isEmpty();
        }
        assertThat(transcripts.missedStop(directory.resolve("absent.jsonl"), LAUNCHED)).isEmpty();
    }

    @Test
    void readsWhenATurnEndedOnAnApiErrorAndWhichError() throws IOException {
        Instant ended = Instant.parse("2026-10-16T10:15:00.080Z");
        Path unnamed = directory.resolve("unnamed-error-stop.jsonl");
        Files.writeString(unnamed, Files.readString(fixture("error-stop.jsonl")).replace("\"error\":\"overloaded\",", ""));

        assertThat(transcripts.errorStop(fixture("error-stop.jsonl"), LAUNCHED)).contains(new ErrorStop(ended, "overloaded"));
        assertThat(transcripts.errorStop(fixture("error-stop.jsonl"), ended.plusMillis(1))).isEmpty();
        assertThat(transcripts.errorStop(unnamed, LAUNCHED)).contains(new ErrorStop(ended, "unknown"));
        for (String fixture : new String[] {"missed-stop.jsonl", "answered-stop.jsonl", "working.jsonl", "overloaded.jsonl", "truncated.jsonl"}) {
            assertThat(transcripts.errorStop(fixture(fixture), LAUNCHED)).as(fixture).isEmpty();
        }
        assertThat(transcripts.errorStop(directory.resolve("absent.jsonl"), LAUNCHED)).isEmpty();
        assertThat(transcripts.usageLimitStop(fixture("error-stop.jsonl"), LAUNCHED)).isEmpty();
        assertThat(transcripts.contextLimitStop(fixture("error-stop.jsonl"), LAUNCHED)).isEmpty();
        assertThat(transcripts.missedStop(fixture("error-stop.jsonl"), LAUNCHED)).isEmpty();
    }

    @Test
    void readsOnlyTheTailOfALongTranscript() throws IOException {
        Path transcript = directory.resolve("long.jsonl");
        String filler = "{\"type\":\"assistant\",\"message\":{\"id\":\"filler\",\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"" + "x".repeat(1_000) + "\"}]},"
                + "\"timestamp\":\"2026-10-16T09:30:00.000Z\"}\n";
        Files.writeString(transcript, filler.repeat(400) + Files.readString(fixture("session-limit.jsonl")));

        assertThat(Files.size(transcript)).isGreaterThan(256 * 1024);
        assertThat(transcripts.usageLimitStop(transcript, LAUNCHED)).contains(new LimitStop(LIMIT_HIT, new UsageLimit("session limit", RESET)));
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
