package io.github.ythibos.edi.web.report;

import static java.util.function.Predicate.not;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

final class WordWrap {

    private static final String WORD_BREAK = " ";

    private WordWrap() {
    }

    static List<String> hanging(String text, int width, String lead, String indent) {
        List<String> lines = wrapped(text, width - columns(lead));
        return Stream.concat(Stream.of(lead + lines.getFirst()), lines.stream().skip(1).map(line -> indent + line)).toList();
    }

    static int columns(String text) {
        return text.codePointCount(0, text.length());
    }

    private static List<String> wrapped(String text, int width) {
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : wordsOf(text)) {
            if (line.isEmpty()) {
                line = word;
            } else if (columns(line) + WORD_BREAK.length() + columns(word) <= width) {
                line = line + WORD_BREAK + word;
            } else {
                lines.add(line);
                line = word;
            }
        }
        lines.add(line);
        return lines;
    }

    private static List<String> wordsOf(String text) {
        return Arrays.stream(text.split(WORD_BREAK)).filter(not(String::isEmpty)).toList();
    }
}
