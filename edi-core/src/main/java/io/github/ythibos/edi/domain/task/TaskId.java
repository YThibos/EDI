package io.github.ythibos.edi.domain.task;

import java.util.Locale;

import io.github.ythibos.edi.domain.AsciiCharacters;

public record TaskId(String value) {

    private static final int LONGEST = 64;
    private static final int LONGEST_SLUG = 40;
    private static final char HYPHEN = '-';
    private static final String DOUBLE_HYPHEN = "--";

    public TaskId {
        if (!isWritten(value)) {
            throw new IllegalArgumentException("A task id is a kebab-case slug of at most " + LONGEST
                    + " characters, such as fix-login-timeout: " + value);
        }
    }

    public static boolean isWritten(String text) {
        return text != null
                && !text.isEmpty()
                && text.length() <= LONGEST
                && text.charAt(0) != HYPHEN
                && text.charAt(text.length() - 1) != HYPHEN
                && !text.contains(DOUBLE_HYPHEN)
                && text.chars().allMatch(character -> AsciiCharacters.isLowerCaseLetterOrDigit(character) || character == HYPHEN);
    }

    public static TaskId sluggedFrom(String title) {
        String words = hyphenated(title.toLowerCase(Locale.ROOT));
        String slug = shortened(words);
        if (slug.isEmpty()) {
            throw new IllegalArgumentException("The title \"" + title + "\" gives no task id; choose one.");
        }
        return new TaskId(slug);
    }

    public TaskId withSuffix(int number) {
        return new TaskId(value + HYPHEN + number);
    }

    @Override
    public String toString() {
        return value;
    }

    private static String hyphenated(String text) {
        StringBuilder slug = new StringBuilder();
        for (char character : text.toCharArray()) {
            if (AsciiCharacters.isLowerCaseLetterOrDigit(character)) {
                slug.append(character);
            } else if (!slug.isEmpty() && slug.charAt(slug.length() - 1) != HYPHEN) {
                slug.append(HYPHEN);
            }
        }
        return withoutTrailingHyphen(slug.toString());
    }

    private static String shortened(String slug) {
        if (slug.length() <= LONGEST_SLUG) {
            return slug;
        }
        int lastWordBreak = slug.lastIndexOf(HYPHEN, LONGEST_SLUG);
        return withoutTrailingHyphen(slug.substring(0, lastWordBreak > 0 ? lastWordBreak : LONGEST_SLUG));
    }

    private static String withoutTrailingHyphen(String slug) {
        return slug.endsWith(String.valueOf(HYPHEN)) ? slug.substring(0, slug.length() - 1) : slug;
    }
}
