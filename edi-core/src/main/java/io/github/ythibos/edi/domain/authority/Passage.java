package io.github.ythibos.edi.domain.authority;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
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

    public static Set<Passage> allTypedIn(String prompt) {
        Set<Passage> passages = new LinkedHashSet<>();
        if (!prompt.isBlank()) {
            passages.add(of(prompt));
        }
        prompt.lines().filter(line -> !line.isBlank()).map(Passage::of).forEach(passages::add);
        return passages;
    }

    private static String comparable(String text) {
        String straightQuotes = text.replace('‘', '\'').replace('’', '\'').replace('“', '"').replace('”', '"');
        return WHITESPACE.matcher(straightQuotes).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }
}
