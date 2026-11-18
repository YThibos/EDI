package io.github.ythibos.edi.domain.task;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public enum TaskSize {
    XS,
    S,
    M,
    L;

    private static final Pattern BACKLOG_ITEM_SIZE_ROW = Pattern.compile("^\\|\\s*Size\\s*\\|\\s*([^|]*?)\\s*\\|\\s*$", Pattern.MULTILINE);

    public static TaskSize named(String word) {
        return find(word).orElseThrow(() -> new IllegalArgumentException("A size is " + everyWord() + ", not " + word + "."));
    }

    public static Optional<TaskSize> find(String word) {
        return Arrays.stream(values()).filter(size -> size.word().equals(word)).findFirst();
    }

    public static Optional<TaskSize> ofBacklogItem(String body) {
        Matcher sizeRow = BACKLOG_ITEM_SIZE_ROW.matcher(body);
        return sizeRow.find() ? Optional.of(named(sizeRow.group(1))) : Optional.empty();
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
}
