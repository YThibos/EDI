package io.github.ythibos.edi.web.report;

import java.util.ArrayList;
import java.util.List;

import io.github.ythibos.edi.domain.report.TurnItem;

final class ReportLines {

    private static final String SECTION_RULE = "─";

    private ReportLines() {
    }

    static List<String> standing(String line, int width) {
        return WordWrap.hanging(Glyphs.safe(line), width, "", "  ");
    }

    static List<String> countedSection(String title, List<ReportItem> items, String placeholder, int width) {
        return headed(counted(title, items.size()), ProjectColumn.lines(items, placeholder, width));
    }

    static List<String> headed(String heading, List<String> body) {
        List<String> lines = new ArrayList<>(List.of(heading, SECTION_RULE.repeat(WordWrap.columns(heading))));
        lines.addAll(body);
        return lines;
    }

    static String counted(String title, int count) {
        return title + "  (" + count + ")";
    }

    static List<ReportItem> turnItems(List<TurnItem> items) {
        return items.stream().map(item -> new ReportItem(new ProjectSet(item.projects()), item.text())).toList();
    }

    static String withoutTrailingSpaces(String line) {
        int end = line.length();
        while (end > 0 && line.charAt(end - 1) == ' ') {
            end--;
        }
        return line.substring(0, end);
    }
}
