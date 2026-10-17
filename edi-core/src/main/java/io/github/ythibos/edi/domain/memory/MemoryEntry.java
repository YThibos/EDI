package io.github.ythibos.edi.domain.memory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MemoryEntry(String firstLine, Optional<LocalDate> dated) {

    private static final Pattern ITEM = Pattern.compile("^[-*]\\s+.*");
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final int SHOWN = 80;

    static List<MemoryEntry> allIn(String text) {
        List<MemoryEntry> entries = new ArrayList<>();
        List<String> lines = text.lines().toList();
        int start = -1;
        for (int index = 0; index < lines.size(); index++) {
            if (ITEM.matcher(lines.get(index)).matches()) {
                addEntry(entries, lines, start, index);
                start = index;
            } else if (start >= 0 && !continues(lines.get(index))) {
                addEntry(entries, lines, start, index);
                start = -1;
            }
        }
        addEntry(entries, lines, start, lines.size());
        return List.copyOf(entries);
    }

    public String shown() {
        return firstLine.length() <= SHOWN ? firstLine : firstLine.substring(0, SHOWN - 3) + "...";
    }

    private static boolean continues(String line) {
        return line.startsWith(" ") || line.startsWith("\t");
    }

    private static void addEntry(List<MemoryEntry> entries, List<String> lines, int start, int end) {
        if (start < 0) {
            return;
        }
        String entry = String.join("\n", lines.subList(start, end));
        entries.add(new MemoryEntry(lines.get(start).replaceFirst("^[-*]\\s+", "").strip(), latestDateIn(entry)));
    }

    private static Optional<LocalDate> latestDateIn(String entry) {
        Matcher dates = ISO_DATE.matcher(entry);
        Optional<LocalDate> latest = Optional.empty();
        while (dates.find()) {
            Optional<LocalDate> found = dateOf(dates.group(1));
            if (found.isPresent() && (latest.isEmpty() || found.get().isAfter(latest.get()))) {
                latest = found;
            }
        }
        return latest;
    }

    private static Optional<LocalDate> dateOf(String written) {
        try {
            return Optional.of(LocalDate.parse(written));
        } catch (DateTimeParseException notADate) {
            return Optional.empty();
        }
    }
}
