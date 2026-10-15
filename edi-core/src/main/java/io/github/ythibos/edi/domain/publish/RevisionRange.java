package io.github.ythibos.edi.domain.publish;

public record RevisionRange(String expression) {

    public RevisionRange {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("A revision range names the outgoing commits, for example origin/main..HEAD.");
        }
        if (expression.startsWith("-") || expression.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("A revision range is one git revision expression, not options: " + expression);
        }
    }

    @Override
    public String toString() {
        return expression;
    }
}
