package io.github.ythibos.edi.domain.status;

import io.github.ythibos.edi.domain.AsciiCharacters;

public record StatusKey(String value) {

    private static final int LONGEST = 80;
    private static final String SEPARATORS = "._-";

    public StatusKey {
        if (!isKey(value)) {
            throw new IllegalArgumentException("A status key is letters, digits, dots, dashes and underscores, such as nm-42-review: " + value);
        }
    }

    private static boolean isKey(String value) {
        return value != null
                && !value.isEmpty()
                && value.length() <= LONGEST
                && AsciiCharacters.isLetterOrDigit(value.charAt(0))
                && value.chars().allMatch(character -> AsciiCharacters.isLetterOrDigit(character) || SEPARATORS.indexOf(character) >= 0);
    }

    @Override
    public String toString() {
        return value;
    }
}
