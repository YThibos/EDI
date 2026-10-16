package io.github.ythibos.edi.web.report;

final class Glyphs {

    private static final int REPLACEMENT = '?';
    private static final int SPACE = ' ';
    private static final int LAST_PRINTABLE_ASCII = '~';
    private static final int NO_BREAK_SPACE = 0x00A0;
    private static final int LAST_OF_LATIN_EXTENDED_A = 0x017F;
    private static final int HYPHEN = 0x2010;
    private static final int HYPHENATION_POINT = 0x2027;
    private static final int EURO_SIGN = 0x20AC;

    private Glyphs() {
    }

    static String safe(String text) {
        return text.codePoints()
                .map(Glyphs::singleWidth)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private static int singleWidth(int character) {
        if (isLineBreakOrTab(character)) {
            return SPACE;
        }
        return isOnTheAllowList(character) ? character : REPLACEMENT;
    }

    private static boolean isLineBreakOrTab(int character) {
        return character == '\n' || character == '\r' || character == '\t';
    }

    private static boolean isOnTheAllowList(int character) {
        return isWithin(character, SPACE, LAST_PRINTABLE_ASCII)
                || isWithin(character, NO_BREAK_SPACE, LAST_OF_LATIN_EXTENDED_A)
                || isWithin(character, HYPHEN, HYPHENATION_POINT)
                || character == EURO_SIGN;
    }

    private static boolean isWithin(int character, int first, int last) {
        return character >= first && character <= last;
    }
}
