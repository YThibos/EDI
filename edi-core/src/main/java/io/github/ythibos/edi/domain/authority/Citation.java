package io.github.ythibos.edi.domain.authority;

public sealed interface Citation {

    static Citation of(Integer message, Integer line, String text) {
        if ((message == null) == (text == null)) {
            throw new IllegalArgumentException("The captain's words are cited by the number of their message or by their text, one of the two.");
        }
        if (message == null && line != null) {
            throw new IllegalArgumentException("A line is cited within a message, so the line takes the message's number.");
        }
        if (message == null) {
            return new Verbatim(text);
        }
        return line == null ? new WholeMessage(message) : new OneLine(message, line);
    }

    record WholeMessage(int number) implements Citation {
    }

    record OneLine(int message, int line) implements Citation {
    }

    record Verbatim(String words) implements Citation {
    }
}
