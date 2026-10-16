package io.github.ythibos.edi.domain.project;

import io.github.ythibos.edi.domain.AsciiCharacters;

public record ProjectKey(String value) {

    private static final int LONGEST = 16;

    public ProjectKey {
        if (!isKeyPrefix(value)) {
            throw new IllegalArgumentException("A project key is an upper-case letter followed by upper-case letters or digits,"
                    + " at most " + LONGEST + " of them, such as EDI: " + value);
        }
    }

    private static boolean isKeyPrefix(String value) {
        return value != null
                && !value.isEmpty()
                && value.length() <= LONGEST
                && AsciiCharacters.isUpperCaseLetter(value.charAt(0))
                && value.chars().allMatch(AsciiCharacters::isUpperCaseLetterOrDigit);
    }

    @Override
    public String toString() {
        return value;
    }
}
