package io.github.ythibos.edi.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class RuleCatalogue {

    private static final Path CATALOGUE = Path.of(System.getProperty("edi.repository.root", "..")).toAbsolutePath().normalize()
            .resolve("docs/rules.md");
    private static final Pattern CATALOGUE_ROW = Pattern.compile("^\\| `([^`]+)` \\| ([^|]*) \\| ([^|]*) \\|$");
    private static final Pattern PLACE = Pattern.compile("`([^`]+)`");

    private final Map<String, Entry> entries;

    private RuleCatalogue(Map<String, Entry> entries) {
        this.entries = entries;
    }

    public static RuleCatalogue ofTheRepository() {
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (String line : linesOf(CATALOGUE)) {
            Matcher row = CATALOGUE_ROW.matcher(line);
            if (row.matches()) {
                entries.put(row.group(1), new Entry(placesIn(row.group(2)), row.group(3)));
            }
        }
        return new RuleCatalogue(entries);
    }

    public Map<String, Entry> entries() {
        return entries;
    }

    public Set<String> rulesFiredBy(String place) {
        return entries.entrySet().stream().filter(entry -> entry.getValue().places().contains(place)).map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static List<String> linesOf(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static List<String> placesIn(String cell) {
        return PLACE.matcher(cell).results().map(found -> found.group(1)).toList();
    }

    public record Entry(List<String> places, String protects) {
    }
}
