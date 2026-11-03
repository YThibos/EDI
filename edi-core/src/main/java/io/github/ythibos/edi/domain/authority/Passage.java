package io.github.ythibos.edi.domain.authority;

import java.util.Locale;
import java.util.regex.Pattern;

public record Passage(String words) {

    private static final Pattern WHITESPACE = Pattern.compile("(?U)\\s+");

    public Passage {
        if (words.isBlank()) {
            throw new IllegalArgumentException("A passage holds some of the captain's words.");
        }
    }

    public static Passage of(String text) {
        return new Passage(comparable(text));
    }

    private static String comparable(String text) {
        String straightQuotes = text.replace('‘', '\'').replace('’', '\'').replace('“', '"').replace('”', '"');
        return WHITESPACE.matcher(straightQuotes).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }
}
