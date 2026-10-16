package io.github.ythibos.edi.domain.project;

import io.github.ythibos.edi.domain.AsciiCharacters;

public record ProjectName(String value) {

    private static final int LONGEST = 64;
    private static final String SEPARATORS = "._-";

    public ProjectName {
        if (!isRegistryName(value)) {
            throw new IllegalArgumentException("A project name starts with a letter or digit and holds only letters, digits,"
                    + " dots, dashes and underscores, at most " + LONGEST + " of them: " + value);
        }
    }

    private static boolean isRegistryName(String value) {
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
