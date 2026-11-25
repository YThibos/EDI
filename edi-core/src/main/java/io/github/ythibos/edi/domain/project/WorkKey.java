package io.github.ythibos.edi.domain.project;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.ythibos.edi.domain.AsciiCharacters;

public record WorkKey(ProjectKey project, int number) {

    private static final char SEPARATOR = '-';
    private static final int MOST_DIGITS = 9;
    private static final Pattern LEADING_KEY = Pattern.compile("[A-Z][A-Z0-9]*-[0-9]{1," + MOST_DIGITS + "}(?![0-9A-Za-z])");

    public WorkKey {
        if (number < 1) {
            throw new IllegalArgumentException("A work key numbers its work from 1: " + project + SEPARATOR + number);
        }
    }

    public static WorkKey parse(String text) {
        int separator = text.lastIndexOf(SEPARATOR);
        String digits = text.substring(separator + 1);
        if (separator < 1 || digits.length() > MOST_DIGITS || !AsciiCharacters.isDigits(digits)) {
            throw new IllegalArgumentException("A work key is a project key, a dash and a number, such as EDI-3: " + text);
        }
        return new WorkKey(new ProjectKey(text.substring(0, separator)), Integer.parseInt(digits));
    }

    public static Optional<WorkKey> opening(String text) {
        Matcher leading = LEADING_KEY.matcher(text);
        return leading.lookingAt() ? Optional.of(parse(leading.group())) : Optional.empty();
    }

    @Override
    public String toString() {
        return project.value() + SEPARATOR + number;
    }
}
