package io.github.ythibos.edi.domain.decision;

import io.github.ythibos.edi.domain.AsciiCharacters;

public record DecisionNumber(int value) {

    private static final char PREFIX = 'D';
    private static final int MOST_DIGITS = 9;

    public DecisionNumber {
        if (value < 1) {
            throw new IllegalArgumentException("Decision numbers start at D1: " + value);
        }
    }

    public static boolean isWritten(String text) {
        return text.length() > 1 && text.charAt(0) == PREFIX && isNumber(text.substring(1));
    }

    public static DecisionNumber parse(String text) {
        if (!isWritten(text)) {
            throw new IllegalArgumentException("A decision number is D followed by digits, such as D58: " + text);
        }
        return new DecisionNumber(Integer.parseInt(text.substring(1)));
    }

    @Override
    public String toString() {
        return PREFIX + Integer.toString(value);
    }

    private static boolean isNumber(String digits) {
        return digits.length() <= MOST_DIGITS && AsciiCharacters.isDigits(digits);
    }
}
