package io.github.ythibos.edi.domain.publish;

import java.util.List;
import java.util.stream.Stream;

public record RevisionRange(String expression, List<String> exclusions) {

    public RevisionRange {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("A revision range names the outgoing commits, for example origin/main..HEAD.");
        }
        exclusions = List.copyOf(exclusions);
        Stream.concat(Stream.of(expression), exclusions.stream()).forEach(RevisionRange::requireOneRevisionExpression);
    }

    public RevisionRange(String expression) {
        this(expression, List.of());
    }

    public List<String> revisions() {
        return Stream.concat(Stream.of(expression), exclusions.stream().map(RevisionRange::negated)).toList();
    }

    @Override
    public String toString() {
        return exclusions.isEmpty() ? expression : expression + " excluding " + String.join(", ", exclusions);
    }

    private static void requireOneRevisionExpression(String revision) {
        if (revision.isBlank() || revision.startsWith("-") || revision.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("A revision range is made of git revision expressions, not options: " + revision);
        }
    }

    private static String negated(String excluded) {
        return "^" + excluded;
    }
}
