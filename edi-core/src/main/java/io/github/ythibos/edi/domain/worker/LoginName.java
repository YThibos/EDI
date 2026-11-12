package io.github.ythibos.edi.domain.worker;

import io.github.ythibos.edi.domain.AsciiCharacters;

public record LoginName(String value) {

    public static final LoginName DEFAULT = new LoginName("default");

    private static final int LONGEST = 32;
    private static final char SEPARATOR = '-';

    public LoginName {
        if (!isLoginName(value)) {
            throw new IllegalArgumentException("A Claude login's name starts with a lower-case letter or digit and holds only lower-case letters,"
                    + " digits and dashes, at most " + LONGEST + " of them: " + value);
        }
    }

    public static LoginName orDefault(String value) {
        return value == null ? DEFAULT : new LoginName(value);
    }

    public boolean isTheDefault() {
        return equals(DEFAULT);
    }

    private static boolean isLoginName(String value) {
        return value != null
                && !value.isEmpty()
                && value.length() <= LONGEST
                && AsciiCharacters.isLowerCaseLetterOrDigit(value.charAt(0))
                && value.chars().allMatch(character -> AsciiCharacters.isLowerCaseLetterOrDigit(character) || character == SEPARATOR);
    }

    @Override
    public String toString() {
        return value;
    }
}
