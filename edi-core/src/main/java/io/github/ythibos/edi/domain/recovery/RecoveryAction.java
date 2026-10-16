package io.github.ythibos.edi.domain.recovery;

import java.util.Arrays;
import java.util.Locale;

public enum RecoveryAction {
    RESUME,
    FRESH;

    public static RecoveryAction named(String word) {
        return Arrays.stream(values())
                .filter(action -> action.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("An entry on the outage hold list is answered with resume or fresh, not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }
}
