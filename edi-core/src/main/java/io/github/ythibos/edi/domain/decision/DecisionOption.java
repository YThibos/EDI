package io.github.ythibos.edi.domain.decision;

import io.github.ythibos.edi.domain.authority.CaptainWordAct;

public record DecisionOption(OptionKey key, String text, CaptainWordAct authorises) {

    public DecisionOption {
        text = text.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Option " + key + " says what it would do.");
        }
    }

    public DecisionOption(OptionKey key, String text) {
        this(key, text, null);
    }

    public boolean authorises(CaptainWordAct act) {
        return act == authorises;
    }
}
