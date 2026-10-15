package io.github.ythibos.edi.domain.decision;

import java.util.Arrays;
import java.util.Locale;

public enum AnswerChannel {
    CHAT,
    CONSOLE,
    BOARD;

    public static AnswerChannel named(String word) {
        return Arrays.stream(values())
                .filter(channel -> channel.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("An answer comes through chat, the console or a board, not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }
}
