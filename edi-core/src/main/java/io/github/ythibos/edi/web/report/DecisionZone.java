package io.github.ythibos.edi.web.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.DecisionOption;
import io.github.ythibos.edi.domain.decision.Recommendation;

final class DecisionZone {

    private static final String TOP_RULE = "╔══ NEEDS YOUR DECISION ══ ";
    private static final String DOUBLE_RULE = "═";
    private static final String BOTTOM_CORNER = "╚";
    private static final String RAIL = "║";
    private static final String ON_THE_RAIL = "║  ";
    private static final String NOTHING_WAITING = "║  Nothing waiting on you";
    private static final String GROUP_RULE = "╟── ";
    private static final String SINGLE_RULE = "─";
    private static final int GROUP_RULE_COLUMNS = 40;
    private static final int SHORTEST_GROUP_TAIL = 4;
    private static final int RAIL_COLUMNS = 3;
    private static final int NUMBER_COLUMNS = 6;
    private static final String CARD_INDENT = " ".repeat(NUMBER_COLUMNS);
    private static final String OPTION_INDENT = " ".repeat(11);
    private static final String RECOMMENDATION_INDENT = " ".repeat(8);

    private DecisionZone() {
    }

    static List<String> lines(List<Entry> decisions, int width) {
        List<String> lines = new ArrayList<>();
        lines.add(topRule(decisions.size(), width));
        groupsOf(decisions).forEach((title, group) -> lines.addAll(groupLines(title, group, width - RAIL_COLUMNS)));
        lines.add(decisions.isEmpty() ? NOTHING_WAITING : RAIL);
        lines.add(BOTTOM_CORNER + DOUBLE_RULE.repeat(width - 1));
        return lines;
    }

    private static String topRule(int open, int width) {
        String top = TOP_RULE + (open > 0 ? open + " open " : "nothing open ");
        return top + DOUBLE_RULE.repeat(Math.max(0, width - WordWrap.columns(top)));
    }

    private static Map<String, List<Entry>> groupsOf(List<Entry> decisions) {
        return decisions.stream().collect(Collectors.groupingBy(entry -> entry.projects().title(), LinkedHashMap::new, Collectors.toList()));
    }

    private static List<String> groupLines(String title, List<Entry> group, int boxWidth) {
        List<String> lines = new ArrayList<>();
        lines.add(RAIL);
        lines.add(GROUP_RULE + title + " " + SINGLE_RULE.repeat(Math.max(SHORTEST_GROUP_TAIL, GROUP_RULE_COLUMNS - WordWrap.columns(title))));
        group.forEach(entry -> lines.addAll(decisionLines(entry, boxWidth)));
        return lines;
    }

    private static List<String> decisionLines(Entry entry, int boxWidth) {
        DecisionCard card = entry.card();
        List<String> lines = new ArrayList<>();
        lines.add(RAIL);
        lines.addAll(onTheRail(WordWrap.hanging(Glyphs.safe(card.title()), boxWidth, numbered(entry.number()), CARD_INDENT)));
        if (!card.context().isEmpty()) {
            lines.addAll(onTheRail(WordWrap.hanging(Glyphs.safe(card.context()), boxWidth, CARD_INDENT, CARD_INDENT)));
        }
        card.options().forEach(option -> lines.addAll(optionLines(option, boxWidth)));
        lines.addAll(recommendationLines(card.recommendation(), boxWidth));
        return lines;
    }

    private static List<String> onTheRail(List<String> lines) {
        return lines.stream().map(line -> ON_THE_RAIL + line).toList();
    }

    private static String numbered(DecisionNumber number) {
        String written = number.toString();
        return written + " ".repeat(Math.max(0, NUMBER_COLUMNS - written.length()));
    }

    private static List<String> optionLines(DecisionOption option, int boxWidth) {
        List<String> lines = new ArrayList<>();
        lines.add(RAIL);
        lines.addAll(onTheRail(WordWrap.hanging(Glyphs.safe(option.text()), boxWidth, CARD_INDENT + "(" + option.key() + ")  ", OPTION_INDENT)));
        return lines;
    }

    private static List<String> recommendationLines(Recommendation recommendation, int boxWidth) {
        String recommended = "» Recommended: " + recommendation.key() + " - " + Glyphs.safe(recommendation.why());
        List<String> lines = new ArrayList<>();
        lines.add(RAIL);
        lines.addAll(onTheRail(WordWrap.hanging(recommended, boxWidth, CARD_INDENT, RECOMMENDATION_INDENT)));
        return lines;
    }

    record Entry(DecisionNumber number, ProjectSet projects, DecisionCard card) {
    }
}
