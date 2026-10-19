package io.github.ythibos.edi.web.statusline;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

import io.github.ythibos.edi.domain.statusline.ClaudeSessionStatus;
import io.github.ythibos.edi.domain.statusline.SessionTokens;
import io.github.ythibos.edi.domain.statusline.UsageLimit;
import tools.jackson.databind.JsonNode;

final class StatusLinePayload {

    private StatusLinePayload() {
    }

    static ClaudeSessionStatus sessionIn(JsonNode payload) {
        JsonNode context = payload.path("context_window");
        JsonNode limits = payload.path("rate_limits");
        return new ClaudeSessionStatus(
                textIn(payload.path("model").path("display_name")),
                textIn(payload.path("effort").path("level")),
                percentageIn(context.path("used_percentage")),
                tokensIn(context),
                limitIn(limits.path("five_hour")),
                limitIn(limits.path("seven_day")));
    }

    static Path workingDirectoryIn(JsonNode payload) {
        return textIn(payload.path("workspace").path("current_dir"))
                .or(() -> textIn(payload.path("cwd")))
                .map(Path::of)
                .orElseThrow(() -> new IllegalArgumentException("A status line names its working directory in workspace.current_dir or cwd."));
    }

    private static Optional<String> textIn(JsonNode field) {
        return field.isString() && !field.asString().isBlank() ? Optional.of(field.asString()) : Optional.empty();
    }

    private static OptionalInt percentageIn(JsonNode field) {
        return field.isNumber() ? OptionalInt.of(wholePercentage(field)) : OptionalInt.empty();
    }

    private static int wholePercentage(JsonNode field) {
        return (int) Math.floor(field.asDouble());
    }

    private static SessionTokens tokensIn(JsonNode context) {
        JsonNode lastCall = context.path("current_usage");
        return new SessionTokens(
                countIn(context.path("total_input_tokens")) + countIn(context.path("total_output_tokens")),
                countIn(lastCall.path("cache_read_input_tokens")),
                countIn(lastCall.path("input_tokens")) + countIn(lastCall.path("cache_creation_input_tokens")));
    }

    private static long countIn(JsonNode field) {
        return field.isNumber() ? field.asLong() : 0;
    }

    private static Optional<UsageLimit> limitIn(JsonNode window) {
        JsonNode used = window.path("used_percentage");
        return used.isNumber() ? Optional.of(new UsageLimit(wholePercentage(used), momentIn(window.path("resets_at")))) : Optional.empty();
    }

    private static Optional<Instant> momentIn(JsonNode field) {
        return field.isNumber() ? Optional.of(Instant.ofEpochSecond(field.asLong())) : Optional.empty();
    }
}
