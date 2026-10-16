package io.github.ythibos.edi.domain.status;

import java.util.List;
import java.util.Locale;

public record CaptainAddress(String word) {

    public static final CaptainAddress DEFAULT = new CaptainAddress("Commander");

    private static final String CAPTAIN = "Captain";
    private static final String VOCATIVE_MARK = ",";

    public CaptainAddress {
        if (word == null || word.isBlank() || word.strip().contains(" ")) {
            throw new IllegalArgumentException("The captain's address word is one word, such as Commander: " + word);
        }
        word = word.strip();
    }

    public boolean opens(String text) {
        String opening = text.stripLeading().toLowerCase(Locale.ROOT);
        return List.of(word, CAPTAIN).stream()
                .map(address -> address.toLowerCase(Locale.ROOT) + VOCATIVE_MARK)
                .anyMatch(opening::startsWith);
    }

    public String vocative() {
        return word + VOCATIVE_MARK;
    }
}
