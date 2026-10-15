package io.github.ythibos.edi.domain.decision;

import io.github.ythibos.edi.domain.AsciiCharacters;

public record OptionKey(char letter) {

    public OptionKey {
        if (!AsciiCharacters.isUpperCaseLetter(letter)) {
            throw new IllegalArgumentException("An option key is one capital letter, such as A: " + letter);
        }
    }

    public static OptionKey parse(String text) {
        if (text.length() != 1) {
            throw new IllegalArgumentException("An option key is one capital letter, such as A: " + text);
        }
        return new OptionKey(text.charAt(0));
    }

    @Override
    public String toString() {
        return String.valueOf(letter);
    }
}
