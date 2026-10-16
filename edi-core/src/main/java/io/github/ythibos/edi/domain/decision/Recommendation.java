package io.github.ythibos.edi.domain.decision;

public record Recommendation(OptionKey key, String why) {

    public Recommendation {
        why = why.strip();
        if (why.isEmpty()) {
            throw new IllegalArgumentException("The recommendation of option " + key + " says why.");
        }
    }
}
