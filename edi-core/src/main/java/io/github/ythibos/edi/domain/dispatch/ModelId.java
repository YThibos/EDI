package io.github.ythibos.edi.domain.dispatch;

import io.github.ythibos.edi.domain.AsciiCharacters;

public record ModelId(String value) {

    private static final int LONGEST = 64;
    private static final String SEPARATORS = ".-";

    public ModelId {
        if (!isModelName(value)) {
            throw new IllegalArgumentException("A model id is lower-case letters, digits, dots and dashes, such as claude-opus-5-5: " + value);
        }
    }

    private static boolean isModelName(String value) {
        return value != null
                && !value.isEmpty()
                && value.length() <= LONGEST
                && AsciiCharacters.isLowerCaseLetterOrDigit(value.charAt(0))
                && value.chars().allMatch(character -> AsciiCharacters.isLowerCaseLetterOrDigit(character) || SEPARATORS.indexOf(character) >= 0);
    }

    @Override
    public String toString() {
        return value;
    }
}
