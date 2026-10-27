package io.github.ythibos.edi.application.brief;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.ythibos.edi.domain.Refusal;

final class BriefLength {

    private static final int MOST_LINES = 120;

    private BriefLength() {
    }

    static void requireWithinTheLimit(String brief) {
        List<String> lines = brief.lines().toList();
        if (lines.size() > MOST_LINES) {
            Section longest = longestSectionOf(lines);
            throw new Refusal("The brief would run to " + lines.size() + " lines, over the " + MOST_LINES + " a brief holds; its longest section is "
                    + longest.title() + ", at " + longest.lines() + " lines: shorten it, or point the worker to a file in the task directory.");
        }
    }

    private static Section longestSectionOf(List<String> lines) {
        return linesPerSection(lines).entrySet().stream()
                .map(section -> new Section(section.getKey(), section.getValue()))
                .max(Comparator.comparingInt(Section::lines))
                .orElseThrow();
    }

    private static Map<String, Integer> linesPerSection(List<String> lines) {
        List<String> headings = BriefTemplate.sectionHeadings();
        Map<String, Integer> sections = new LinkedHashMap<>();
        String current = null;
        for (String line : lines) {
            if (headings.contains(line)) {
                current = line;
            }
            if (current != null) {
                sections.merge(current, 1, Integer::sum);
            }
        }
        return sections;
    }

    private record Section(String heading, int lines) {

        String title() {
            return heading.substring(BriefTemplate.SECTION_HEADING.length());
        }
    }
}
