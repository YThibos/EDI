package io.github.ythibos.edi.domain.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BraceExpansion {

    private static final char ESCAPE = '\\';
    private static final Pattern NUMBER_SEQUENCE = Pattern.compile("([-+]?\\d{1,18})\\.\\.([-+]?\\d{1,18})(?:\\.\\.([-+]?\\d{1,18}))?");
    private static final Pattern LETTER_SEQUENCE = Pattern.compile("([A-Za-z])\\.\\.([A-Za-z])(?:\\.\\.([-+]?\\d{1,18}))?");

    private final int most;
    private final List<String> words = new ArrayList<>();

    private BraceExpansion(int most) {
        this.most = most;
    }

    public static List<String> of(String pattern, int most) {
        BraceExpansion expansion = new BraceExpansion(most);
        expansion.expand(pattern, 0);
        return expansion.words;
    }

    private void expand(String pattern, int from) {
        Optional<Braces> braces = firstBracesIn(pattern, from);
        if (braces.isEmpty()) {
            add(pattern);
            return;
        }
        Braces found = braces.get();
        for (String alternative : found.alternatives()) {
            expand(pattern.substring(0, found.open()) + alternative + pattern.substring(found.close() + 1), found.open());
        }
    }

    private Optional<Braces> firstBracesIn(String pattern, int from) {
        for (int at = from; at < pattern.length(); at++) {
            char character = pattern.charAt(at);
            if (character == ESCAPE) {
                at++;
            } else if (character == '{') {
                int close = closingBrace(pattern, at);
                if (close > 0 && startsAParameter(pattern, at)) {
                    at = close;
                } else if (close > 0) {
                    Optional<List<String>> alternatives = alternativesIn(pattern.substring(at + 1, close));
                    if (alternatives.isPresent()) {
                        return Optional.of(new Braces(at, close, alternatives.get()));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static int closingBrace(String pattern, int open) {
        int depth = 0;
        for (int at = open; at < pattern.length(); at++) {
            char character = pattern.charAt(at);
            if (character == ESCAPE) {
                at++;
            } else if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return at;
            }
        }
        return -1;
    }

    private static boolean startsAParameter(String pattern, int brace) {
        return brace > 0 && pattern.charAt(brace - 1) == '$' && (brace < 2 || pattern.charAt(brace - 2) != ESCAPE);
    }

    private Optional<List<String>> alternativesIn(String body) {
        List<String> listed = listedIn(body);
        if (listed.size() > 1) {
            return Optional.of(listed);
        }
        return sequenceIn(body);
    }

    private static List<String> listedIn(String body) {
        List<String> listed = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int at = 0; at < body.length(); at++) {
            char character = body.charAt(at);
            if (character == ESCAPE) {
                at++;
            } else if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
            } else if (character == ',' && depth == 0) {
                listed.add(body.substring(start, at));
                start = at + 1;
            }
        }
        listed.add(body.substring(start));
        return listed;
    }

    private Optional<List<String>> sequenceIn(String body) {
        Matcher numbers = NUMBER_SEQUENCE.matcher(body);
        if (numbers.matches()) {
            return Optional.of(numberSequence(numbers.group(1), numbers.group(2), stepOf(numbers.group(3))));
        }
        Matcher letters = LETTER_SEQUENCE.matcher(body);
        if (letters.matches()) {
            return Optional.of(letterSequence(letters.group(1).charAt(0), letters.group(2).charAt(0), stepOf(letters.group(3))));
        }
        return Optional.empty();
    }

    private static long stepOf(String written) {
        long step = written == null ? 1 : Math.abs(Long.parseLong(written));
        return step == 0 ? 1 : step;
    }

    private List<String> numberSequence(String first, String last, long step) {
        long start = Long.parseLong(first);
        long end = Long.parseLong(last);
        checkSize(Math.abs(end - start) / step + 1);
        int width = isPadded(first) || isPadded(last) ? Math.max(first.length(), last.length()) : 0;
        List<String> sequence = new ArrayList<>();
        long direction = end >= start ? step : -step;
        for (long number = start; direction > 0 ? number <= end : number >= end; number += direction) {
            sequence.add(width > 0 ? String.format("%0" + width + "d", number) : Long.toString(number));
        }
        return sequence;
    }

    private void checkSize(long size) {
        if (size > most) {
            throw tooManyWords();
        }
    }

    private UntraceablePaths tooManyWords() {
        return new UntraceablePaths("its braces expand to more than " + most + " words");
    }

    private static boolean isPadded(String number) {
        String digits = number.startsWith("-") || number.startsWith("+") ? number.substring(1) : number;
        return digits.length() > 1 && digits.startsWith("0");
    }

    private List<String> letterSequence(char first, char last, long step) {
        checkSize(Math.abs(last - first) / step + 1);
        List<String> sequence = new ArrayList<>();
        long direction = last >= first ? step : -step;
        for (long letter = first; direction > 0 ? letter <= last : letter >= last; letter += direction) {
            sequence.add(Character.toString((char) letter));
        }
        return sequence;
    }

    private void add(String word) {
        if (words.size() >= most) {
            throw tooManyWords();
        }
        words.add(word);
    }

    private record Braces(int open, int close, List<String> alternatives) {
    }
}
