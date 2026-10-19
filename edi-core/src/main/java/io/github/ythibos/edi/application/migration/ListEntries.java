package io.github.ythibos.edi.application.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

final class ListEntries {

    private static final String ENTRY_MARK = "- ";
    private static final String BOLD = "**";
    private static final String NEWLINE = "\n";

    private ListEntries() {
    }

    static String edited(String markdown, Function<String, Optional<String>> edit) {
        List<String> kept = new ArrayList<>();
        List<String> entry = new ArrayList<>();
        for (String line : markdown.lines().toList()) {
            if (line.startsWith(ENTRY_MARK)) {
                keepEdited(entry, edit, kept);
                entry.add(line);
            } else if (!entry.isEmpty() && continuesAnEntry(line)) {
                entry.add(line);
            } else {
                keepEdited(entry, edit, kept);
                kept.add(line);
            }
        }
        keepEdited(entry, edit, kept);
        return withoutRepeatedBlankLines(String.join(NEWLINE, kept));
    }

    static String titleOf(String entry) {
        String first = entry.lines().findFirst().orElse("").substring(ENTRY_MARK.length());
        int boldEnd = first.indexOf(BOLD, BOLD.length());
        return first.startsWith(BOLD) && boldEnd > 0 ? first.substring(BOLD.length(), boldEnd) : first;
    }

    private static boolean continuesAnEntry(String line) {
        return line.isBlank() || Character.isWhitespace(line.charAt(0));
    }

    private static void keepEdited(List<String> entry, Function<String, Optional<String>> edit, List<String> kept) {
        if (entry.isEmpty()) {
            return;
        }
        int end = entry.size();
        while (end > 1 && entry.get(end - 1).isBlank()) {
            end--;
        }
        List<String> trailingBlankLines = List.copyOf(entry.subList(end, entry.size()));
        edit.apply(String.join(NEWLINE, entry.subList(0, end))).ifPresent(text -> {
            kept.add(text);
            kept.addAll(trailingBlankLines);
        });
        entry.clear();
    }

    private static String withoutRepeatedBlankLines(String markdown) {
        return markdown.replaceAll("\n{3,}", "\n\n").strip();
    }
}
