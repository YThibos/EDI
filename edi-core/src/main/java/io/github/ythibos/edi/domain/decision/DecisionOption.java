package io.github.ythibos.edi.domain.decision;

public record DecisionOption(OptionKey key, String text) {

    public DecisionOption {
        text = text.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Option " + key + " says what it would do.");
        }
    }
}
