package io.github.ythibos.edi.domain.memory;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MemoryFile(String name, String text) {

    private static final int CHARACTERS_PER_TOKEN = 4;
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public MemoryFile {
        Objects.requireNonNull(name, "A memory file has a name.");
        text = Objects.requireNonNullElse(text, "");
    }

    public int estimatedTokens() {
        return Math.ceilDiv(text.length(), CHARACTERS_PER_TOKEN);
    }

    public List<String> headings() {
        return text.lines().map(HEADING::matcher).filter(Matcher::matches).map(heading -> comparable(heading.group(1))).toList();
    }

    public List<MemoryEntry> entries() {
        return MemoryEntry.allIn(text);
    }

    private static String comparable(String heading) {
        return WHITESPACE.matcher(heading).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }
}
