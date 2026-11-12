package io.github.ythibos.edi.application.wake;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.rule.Rule;

public record StreamPoll(UUID stream, long pid, boolean follows, List<Long> printed, boolean ending) {

    public StreamPoll {
        if (stream == null || pid < 1) {
            throw new Refusal(Rule.ONE_WAKE_STREAM, "edi wait names its stream and its process on every call; this one named neither, so update the edi command.");
        }
        printed = List.copyOf(Objects.requireNonNullElse(printed, List.of()));
    }
}
