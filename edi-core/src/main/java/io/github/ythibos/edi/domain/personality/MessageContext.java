package io.github.ythibos.edi.domain.personality;

public enum MessageContext {

    BAD_NEWS(4),
    DECISION_NEEDED(3),
    FIRST_MESSAGE(3),
    GOOD_NEWS(1),
    STATUS_REPORT(0.7),
    NOTHING_TO_REPORT(0.5);

    private final double weight;

    MessageContext(double weight) {
        this.weight = weight;
    }

    double weight() {
        return weight;
    }
}
