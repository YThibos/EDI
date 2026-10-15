package io.github.ythibos.edi.web.report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.ythibos.edi.domain.AsciiCharacters;

final class ProjectColumn {

    private static final String MARGIN = "  ";
    private static final int LABEL_COLUMNS = 18;
    private static final int LONGEST_LABEL = 16;
    private static final String BULLET = "• ";
    private static final String HANGING_INDENT = MARGIN + " ".repeat(LABEL_COLUMNS + BULLET.length());
    private static final Comparator<Group> FLEET_FIRST_THEN_BY_TITLE = Comparator
            .comparing((Group group) -> !group.isFleet())
            .thenComparing(group -> asciiLowerCase(group.title()));

    private ProjectColumn() {
    }

    static List<String> lines(List<ReportItem> items, String placeholder, int width) {
        if (items.isEmpty()) {
            return List.of(MARGIN + placeholder);
        }
        return groupsOf(items).stream().flatMap(group -> group.lines(width).stream()).toList();
    }

    static String unlabelled(String text) {
        return MARGIN + text;
    }

    private static List<Group> groupsOf(List<ReportItem> items) {
        Map<String, List<ReportItem>> byTitle = items.stream()
                .collect(Collectors.groupingBy(item -> item.projects().title(), LinkedHashMap::new, Collectors.toList()));
        return byTitle.entrySet().stream().map(group -> new Group(group.getKey(), group.getValue())).sorted(FLEET_FIRST_THEN_BY_TITLE).toList();
    }

    private static String asciiLowerCase(String text) {
        return text.chars()
                .map(character -> AsciiCharacters.isUpperCaseLetter(character) ? Character.toLowerCase(character) : character)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private record Group(String title, List<ReportItem> items) {

        boolean isFleet() {
            return title.equals(ProjectSet.FLEET);
        }

        List<String> lines(int width) {
            boolean labelFits = WordWrap.columns(title) <= LONGEST_LABEL;
            List<String> lines = new ArrayList<>();
            if (!labelFits) {
                lines.add(MARGIN + title);
            }
            for (int position = 0; position < items.size(); position++) {
                String label = labelFits && position == 0 ? padded(title) : " ".repeat(LABEL_COLUMNS);
                lines.addAll(WordWrap.hanging(items.get(position).text(), width, MARGIN + label + BULLET, HANGING_INDENT));
            }
            return lines;
        }

        private static String padded(String label) {
            return label + " ".repeat(LABEL_COLUMNS - WordWrap.columns(label));
        }
    }
}
