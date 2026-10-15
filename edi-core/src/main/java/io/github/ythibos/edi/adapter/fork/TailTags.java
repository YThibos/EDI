package io.github.ythibos.edi.adapter.fork;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.ythibos.edi.domain.AsciiCharacters;

final class TailTags {

    private static final List<String> DEPENDENCY_TYPES = List.of(ForkDependency.BLOCKED_BY, "parent", "discovered-from");
    private static final List<String> CLOSING_WORDS = List.of("merged", "reported", "done", "closed");
    private static final Set<String> HOLD_KINDS = Set.of("captain", "external", "load", "parked", "future");
    private static final List<LeadingKind> LEADING_KINDS = List.of(
            new LeadingKind("PERSISTENT SECONDMATE", "secondmate"),
            new LeadingKind("SHIP", "ship"),
            new LeadingKind("SCOUT", "scout"),
            new LeadingKind("DOCS-ONLY", "docs"));
    private static final String REPO = "repo:";
    private static final String DEPENDENCY_REASON_MARK = "-";
    private static final int DATE_LENGTH = "2026-01-01".length();

    private final List<ForkDependency> dependencies = new ArrayList<>();
    private String remaining;
    private String kind;
    private String repo;
    private LocalDate since;
    private LocalDate closed;
    private Integer priority;
    private String holdReason;
    private String holdKind;
    private LocalDate holdUntil;

    private TailTags(String rest) {
        this.remaining = rest;
    }

    static ForkTags of(String rest) {
        TailTags tags = new TailTags(rest);
        tags.stripAll();
        return tags.collected();
    }

    static boolean isForkId(String id) {
        return !id.isEmpty() && AsciiCharacters.isLetterOrDigit(id.charAt(0)) && id.chars().allMatch(TailTags::isIdCharacter);
    }

    private void stripAll() {
        boolean stripped = true;
        while (stripped) {
            stripped = stripDependency() || stripParenthesisedTag();
        }
    }

    private ForkTags collected() {
        String title = remaining.strip();
        ForkHold hold = holdReason == null ? null : new ForkHold(HoldReason.decoded(holdReason), holdKind, holdUntil);
        return new ForkTags(title, kind == null ? leadingKindOf(title) : kind, repo, dependencies, since, closed, priority, hold);
    }

    private boolean stripDependency() {
        String text = remaining.stripTrailing();
        DependencyMarker marker = lastDependencyMarkerIn(text);
        ForkDependency dependency = marker == null ? null : dependencyAfter(marker.type(), marker.textAfterIn(text));
        if (dependency == null) {
            return false;
        }
        dependencies.addFirst(dependency);
        remaining = text.substring(0, marker.at());
        return true;
    }

    private boolean stripParenthesisedTag() {
        String text = remaining.stripTrailing();
        int open = text.lastIndexOf('(');
        if (!text.endsWith(")") || open < 0) {
            return false;
        }
        String content = text.substring(open + 1, text.length() - 1);
        if (content.indexOf(')') >= 0 || !recognised(content)) {
            return false;
        }
        remaining = text.substring(0, open);
        return true;
    }

    private static String leadingKindOf(String title) {
        return LEADING_KINDS.stream()
                .filter(leading -> leading.opens(title))
                .map(LeadingKind::kind)
                .findFirst()
                .orElse(null);
    }

    private static DependencyMarker lastDependencyMarkerIn(String text) {
        DependencyMarker last = null;
        for (String type : DEPENDENCY_TYPES) {
            int at = text.lastIndexOf(type + ":");
            if (at >= 0 && (last == null || at > last.at())) {
                last = new DependencyMarker(type, at);
            }
        }
        return last;
    }

    private static ForkDependency dependencyAfter(String type, String text) {
        int idEnd = firstWhitespaceIn(text);
        String id = text.substring(0, idEnd);
        String rest = text.substring(idEnd).strip();
        if (!isForkId(id)) {
            return null;
        }
        if (rest.isEmpty()) {
            return new ForkDependency(type, id, null);
        }
        return isReason(rest) ? new ForkDependency(type, id, rest.substring(DEPENDENCY_REASON_MARK.length()).strip()) : null;
    }

    private boolean recognised(String content) {
        return recognisedRepo(content)
                || recognisedKind(content)
                || recognisedPriority(content)
                || recognisedSince(content)
                || recognisedClosing(content)
                || recognisedHoldUntil(content)
                || recognisedHoldKind(content)
                || recognisedHold(content);
    }

    private static int firstWhitespaceIn(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (Character.isWhitespace(text.charAt(index))) {
                return index;
            }
        }
        return text.length();
    }

    private static boolean isReason(String rest) {
        return rest.startsWith(DEPENDENCY_REASON_MARK)
                && rest.length() > DEPENDENCY_REASON_MARK.length() + 1
                && Character.isWhitespace(rest.charAt(DEPENDENCY_REASON_MARK.length()));
    }

    private boolean recognisedRepo(String content) {
        int at = content.lastIndexOf(REPO);
        boolean placed = at == 0 || (at > 0 && content.substring(0, at).stripTrailing().endsWith("+"));
        String value = placed ? content.substring(at + REPO.length()).strip() : "";
        if (value.isEmpty()) {
            return false;
        }
        repo = repo == null ? value : repo;
        return true;
    }

    private boolean recognisedKind(String content) {
        String value = valueAfter(content, "kind:");
        if (value == null) {
            return false;
        }
        kind = kind == null ? value : kind;
        return true;
    }

    private boolean recognisedPriority(String content) {
        String value = valueAfter(content, "priority:");
        if (value == null || value.length() != 1 || value.charAt(0) < '0' || value.charAt(0) > '4') {
            return false;
        }
        priority = priority == null ? Integer.valueOf(value) : priority;
        return true;
    }

    private boolean recognisedSince(String content) {
        LocalDate date = dateAfterWord(content, "since");
        if (date == null) {
            return false;
        }
        since = since == null ? date : since;
        return true;
    }

    private boolean recognisedClosing(String content) {
        LocalDate date = CLOSING_WORDS.stream()
                .map(word -> dateAfterWord(content, word))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (date == null) {
            return false;
        }
        closed = closed == null ? date : closed;
        return true;
    }

    private boolean recognisedHoldUntil(String content) {
        String value = valueAfter(content, "hold-until:");
        LocalDate date = value == null ? null : date(value);
        if (date == null) {
            return false;
        }
        holdUntil = holdUntil == null ? date : holdUntil;
        return true;
    }

    private boolean recognisedHoldKind(String content) {
        String value = valueAfter(content, "hold-kind:");
        if (value == null || !HOLD_KINDS.contains(value)) {
            return false;
        }
        holdKind = holdKind == null ? value : holdKind;
        return true;
    }

    private boolean recognisedHold(String content) {
        String value = valueAfter(content, "hold:");
        if (value == null || value.indexOf('(') >= 0) {
            return false;
        }
        holdReason = holdReason == null ? value : holdReason;
        return true;
    }

    private static String valueAfter(String content, String label) {
        if (!content.startsWith(label)) {
            return null;
        }
        String value = content.substring(label.length()).strip();
        return value.isEmpty() ? null : value;
    }

    private static LocalDate dateAfterWord(String content, String word) {
        boolean separated = content.startsWith(word)
                && content.length() > word.length()
                && Character.isWhitespace(content.charAt(word.length()));
        return separated ? date(content.substring(word.length()).strip()) : null;
    }

    private static LocalDate date(String text) {
        if (text.length() != DATE_LENGTH) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException notADate) {
            return null;
        }
    }

    private static boolean isIdCharacter(int character) {
        return AsciiCharacters.isLetterOrDigit(character) || character == '.' || character == '_' || character == '-';
    }

    private record DependencyMarker(String type, int at) {

        String textAfterIn(String text) {
            return text.substring(at + type.length() + 1).stripLeading();
        }
    }

    private record LeadingKind(String word, String kind) {

        boolean opens(String title) {
            return title.startsWith(word) && (title.length() == word.length() || !isWordCharacter(title.charAt(word.length())));
        }

        private static boolean isWordCharacter(char character) {
            return AsciiCharacters.isLetterOrDigit(character) || character == '_';
        }
    }
}
