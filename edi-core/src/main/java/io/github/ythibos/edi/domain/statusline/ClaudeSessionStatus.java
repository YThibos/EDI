package io.github.ythibos.edi.domain.statusline;

import java.util.Optional;
import java.util.OptionalInt;

public record ClaudeSessionStatus(Optional<String> model, Optional<String> effort, OptionalInt contextUsed, SessionTokens tokens,
        Optional<UsageLimit> fiveHourLimit, Optional<UsageLimit> weeklyLimit) {
}
