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
import io.github.ythibos.edi.web.text.WaitingWords;

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
    private static final String BOARD_DECISION_INDENT = " ".repeat(2 * NUMBER_COLUMNS);
    private static final String OPTION_INDENT = " ".repeat(11);
    private static final String RECOMMENDATION_INDENT = " ".repeat(8);

    private DecisionZone() {
    }

    static List<String> lines(List<Entry> waiting, int width) {
        List<String> lines = new ArrayList<>();
        lines.add(topRule(openAmong(waiting), width));
        groupsOf(waiting).forEach((title, group) -> lines.addAll(groupLines(title, group, width - RAIL_COLUMNS)));
        lines.add(waiting.isEmpty() ? NOTHING_WAITING : RAIL);
        lines.add(BOTTOM_CORNER + DOUBLE_RULE.repeat(width - 1));
        return lines;
    }

    private static int openAmong(List<Entry> waiting) {
        return waiting.stream().mapToInt(Entry::open).sum();
    }

    private static String topRule(int open, int width) {
        String top = TOP_RULE + (open > 0 ? open + " open " : "nothing open ");
        return top + DOUBLE_RULE.repeat(Math.max(0, width - WordWrap.columns(top)));
    }

    private static Map<String, List<Entry>> groupsOf(List<Entry> waiting) {
        return waiting.stream().collect(Collectors.groupingBy(entry -> entry.projects().title(), LinkedHashMap::new, Collectors.toList()));
    }

    private static List<String> groupLines(String title, List<Entry> group, int boxWidth) {
        List<String> lines = new ArrayList<>();
        lines.add(RAIL);
        lines.add(GROUP_RULE + title + " " + SINGLE_RULE.repeat(Math.max(SHORTEST_GROUP_TAIL, GROUP_RULE_COLUMNS - WordWrap.columns(title))));
        group.forEach(entry -> lines.addAll(entryLines(entry, boxWidth)));
        return lines;
    }

    private static List<String> entryLines(Entry entry, int boxWidth) {
        return switch (entry) {
            case Call call -> decisionLines(call, boxWidth);
            case BoardQuestions board -> boardLines(board, boxWidth);
            case Review review -> singleLine(WaitingWords.review(review.title(), review.url()), boxWidth);
            case Changing changing -> singleLine(WaitingWords.changing(changing.title(), changing.url(), changing.why()), boxWidth);
        };
    }

    private static List<String> decisionLines(Call call, int boxWidth) {
        DecisionCard card = call.card();
        List<String> lines = new ArrayList<>();
        lines.add(RAIL);
        lines.addAll(onTheRail(WordWrap.hanging(Glyphs.safe(card.title()), boxWidth, numbered(call.number()), CARD_INDENT)));
        if (!card.context().isEmpty()) {
            lines.addAll(onTheRail(WordWrap.hanging(Glyphs.safe(card.context()), boxWidth, CARD_INDENT, CARD_INDENT)));
        }
        card.options().forEach(option -> lines.addAll(optionLines(option, boxWidth)));
        lines.addAll(recommendationLines(card.recommendation(), boxWidth));
        return lines;
    }

    private static List<String> boardLines(BoardQuestions board, int boxWidth) {
        List<String> lines = singleLine(WaitingWords.board(board.title(), board.questions(), board.link()), boxWidth);
        board.decisions().forEach(decision -> lines.addAll(boardDecisionLines(decision, boxWidth)));
        return lines;
    }

    private static List<String> singleLine(String text, int boxWidth) {
        List<String> lines = new ArrayList<>();
        lines.add(RAIL);
        lines.addAll(onTheRail(WordWrap.hanging(Glyphs.safe(text), boxWidth, "", CARD_INDENT)));
        return lines;
    }

    private static List<String> boardDecisionLines(BoardDecision decision, int boxWidth) {
        return onTheRail(WordWrap.hanging(Glyphs.safe(decision.title()), boxWidth, CARD_INDENT + numbered(decision.number()), BOARD_DECISION_INDENT));
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

    sealed interface Entry {

        ProjectSet projects();

        default int open() {
            return 1;
        }
    }

    record Call(DecisionNumber number, ProjectSet projects, DecisionCard card) implements Entry {
    }

    record BoardQuestions(ProjectSet projects, String title, int questions, String link, List<BoardDecision> decisions) implements Entry {

        BoardQuestions {
            decisions = List.copyOf(decisions);
        }

        @Override
        public int open() {
            return (questions > 0 ? 1 : 0) + decisions.size();
        }
    }

    record BoardDecision(DecisionNumber number, String title) {
    }

    record Review(ProjectSet projects, String title, String url) implements Entry {
    }

    record Changing(ProjectSet projects, String title, String url, String why) implements Entry {
    }
}
