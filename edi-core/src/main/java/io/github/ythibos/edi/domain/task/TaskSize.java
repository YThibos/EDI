package io.github.ythibos.edi.domain.task;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public enum TaskSize {
    XS,
    S,
    M,
    L;

    private static final Pattern HEADER_ROW = Pattern.compile("\\|\\s*Field\\s*\\|\\s*Value\\s*\\|\\s*");
    private static final Pattern SEPARATOR_ROW = Pattern.compile("\\|\\s*:?-{3,}:?\\s*\\|\\s*:?-{3,}:?\\s*\\|\\s*");
    private static final Pattern SIZE_ROW = Pattern.compile("\\|\\s*Size\\s*\\|([^|]*)\\|\\s*");
    private static final String TABLE_ROW_START = "|";

    public static TaskSize named(String word) {
        return find(word).orElseThrow(() -> new IllegalArgumentException("A size is " + everyWord() + ", not " + word + "."));
    }

    public static Optional<TaskSize> find(String word) {
        return Arrays.stream(values()).filter(size -> size.word().equals(word)).findFirst();
    }

    public static Optional<TaskSize> ofBacklogItem(String body) {
        return headerTableOf(body).stream()
                .map(SIZE_ROW::matcher)
                .filter(Matcher::matches)
                .findFirst()
                .map(sizeRow -> named(sizeRow.group(1).strip()));
    }

    public static String everyWord() {
        return wordsOf(Arrays.asList(values()));
    }

    public static String wordsOf(Collection<TaskSize> sizes) {
        return sizes.stream().sorted().map(TaskSize::word).collect(Collectors.joining(", "));
    }

    public String word() {
        return name();
    }

    private static List<String> headerTableOf(String body) {
        List<String> lines = body.lines().dropWhile(String::isBlank).toList();
        if (!opensWithTheFieldTable(lines)) {
            return List.of();
        }
        return lines.stream().skip(2).takeWhile(line -> line.startsWith(TABLE_ROW_START)).toList();
    }

    private static boolean opensWithTheFieldTable(List<String> lines) {
        return lines.size() > 1 && HEADER_ROW.matcher(lines.get(0)).matches() && SEPARATOR_ROW.matcher(lines.get(1)).matches();
    }
}
