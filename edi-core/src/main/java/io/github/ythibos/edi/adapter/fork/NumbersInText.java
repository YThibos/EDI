package io.github.ythibos.edi.adapter.fork;

import io.github.ythibos.edi.domain.AsciiCharacters;

final class NumbersInText {

    private static final int MOST_DIGITS = 9;

    private NumbersInText() {
    }

    static int highestAfter(String prefix, String text) {
        int highest = 0;
        for (int at = text.indexOf(prefix); at >= 0; at = text.indexOf(prefix, at + 1)) {
            highest = Math.max(highest, numberAt(text, at, prefix.length()));
        }
        return highest;
    }

    private static int numberAt(String text, int at, int prefixLength) {
        int start = at + prefixLength;
        int end = start;
        while (end < text.length() && AsciiCharacters.isDigit(text.charAt(end))) {
            end++;
        }
        boolean standsAlone = !isWordCharacterAt(text, at - 1) && !isWordCharacterAt(text, end);
        int digits = end - start;
        return standsAlone && digits > 0 && digits <= MOST_DIGITS ? Integer.parseInt(text.substring(start, end)) : 0;
    }

    private static boolean isWordCharacterAt(String text, int index) {
        return index >= 0 && index < text.length() && (AsciiCharacters.isLetterOrDigit(text.charAt(index)) || text.charAt(index) == '_');
    }
}
