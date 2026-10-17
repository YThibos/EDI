package io.github.ythibos.edi.domain.project;

import io.github.ythibos.edi.domain.AsciiCharacters;

public record WorkKey(ProjectKey project, int number) {

    private static final char SEPARATOR = '-';
    private static final int MOST_DIGITS = 9;

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

    @Override
    public String toString() {
        return project.value() + SEPARATOR + number;
    }
}
