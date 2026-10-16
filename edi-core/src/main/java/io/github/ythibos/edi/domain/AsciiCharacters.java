package io.github.ythibos.edi.domain;

public final class AsciiCharacters {

    private AsciiCharacters() {
    }

    public static boolean isLowerCaseLetterOrDigit(int character) {
        return isLowerCaseLetter(character) || isDigit(character);
    }

    public static boolean isUpperCaseLetterOrDigit(int character) {
        return isUpperCaseLetter(character) || isDigit(character);
    }

    public static boolean isLetterOrDigit(int character) {
        return isLowerCaseLetter(character) || isUpperCaseLetter(character) || isDigit(character);
    }

    public static boolean isUpperCaseLetter(int character) {
        return character >= 'A' && character <= 'Z';
    }

    public static boolean isDigits(String text) {
        return !text.isEmpty() && text.chars().allMatch(AsciiCharacters::isDigit);
    }

    public static boolean isDigit(int character) {
        return character >= '0' && character <= '9';
    }

    private static boolean isLowerCaseLetter(int character) {
        return character >= 'a' && character <= 'z';
    }
}
