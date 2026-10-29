package io.github.ythibos.edi.domain.memory;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MemoryFile(String name, String text) {

    public static final String LEARNINGS = "learnings.md";
    private static final int CHARACTERS_PER_TOKEN = 4;
    private static final Pattern HEADING = Pattern.compile("#{1,6}\\s+(\\S.*)");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public MemoryFile {
        Objects.requireNonNull(name, "A memory file has a name.");
        text = Objects.requireNonNullElse(text, "");
    }

    public int estimatedTokens() {
        return Math.ceilDiv(text.length(), CHARACTERS_PER_TOKEN);
    }

    public List<String> headings() {
        return text.lines().map(HEADING::matcher).filter(Matcher::matches).map(heading -> comparable(withoutClosingHashes(heading.group(1)))).toList();
    }

    public List<MemoryEntry> entries() {
        return MemoryEntry.allIn(text);
    }

    public boolean holdsLearnings() {
        return name.equals(LEARNINGS);
    }

    public List<MemoryEntry> workarounds() {
        return holdsLearnings() ? entries().stream().filter(MemoryEntry::isAWorkaround).toList() : List.of();
    }

    private static String withoutClosingHashes(String heading) {
        String title = heading.strip();
        int end = title.length();
        while (end > 0 && title.charAt(end - 1) == '#') {
            end--;
        }
        return title.substring(0, end);
    }

    private static String comparable(String heading) {
        return WHITESPACE.matcher(heading).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }
}
