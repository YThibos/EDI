package io.github.ythibos.edi.domain.authority;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public sealed interface CaptainMessage permits TypedMessage, DialogAnswer {

    UUID session();

    int number();

    Instant sentAt();

    String wholeText();

    List<MessageLine> lines();

    default boolean holdsPastedContent() {
        return lines().stream().anyMatch(MessageLine::pasted);
    }
}
