package io.github.ythibos.edi.application.migration;

import java.util.ArrayList;
import java.util.List;

record MarkdownSections(String preamble, List<Section> sections) {

    private static final String SECTION_MARK = "## ";
    private static final String TITLE_MARK = "# ";
    private static final String LIST_ITEM_MARKS = "-*";

    MarkdownSections {
        sections = List.copyOf(sections);
    }

    static MarkdownSections of(String markdown) {
        List<String> preamble = new ArrayList<>();
        List<List<String>> sections = new ArrayList<>();
        for (String line : markdown.lines().toList()) {
            if (line.startsWith(SECTION_MARK)) {
                sections.add(new ArrayList<>());
            }
            (sections.isEmpty() ? preamble : sections.getLast()).add(line);
        }
        return new MarkdownSections(String.join("\n", preamble).strip(), sections.stream().map(Section::of).toList());
    }

    boolean preambleHoldsEntries() {
        return preamble.lines().anyMatch(line -> !line.isEmpty() && LIST_ITEM_MARKS.indexOf(line.charAt(0)) >= 0);
    }

    String preambleWithoutTitle() {
        return String.join("\n", preamble.lines().filter(line -> !line.startsWith(TITLE_MARK)).toList()).strip();
    }

    record Section(String heading, String text) {

        static Section of(List<String> lines) {
            return new Section(lines.getFirst().substring(SECTION_MARK.length()).strip(), String.join("\n", lines).strip());
        }
    }
}
