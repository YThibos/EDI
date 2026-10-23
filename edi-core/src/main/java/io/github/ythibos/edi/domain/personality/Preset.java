package io.github.ythibos.edi.domain.personality;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public enum Preset implements PersonalityChange {

    BALANCED("Balanced", Personality.of(3, 5, 5, 4, 6, 5, 5, 3, 5)),
    PLAIN("Plain", Personality.of(0, 5, 3, 2, 7, 3, 5, 0, 0)),
    DEMO("Demo", Personality.of(3, 7, 5, 4, 6, 5, 4, 3, 7));

    private final String title;
    private final Personality personality;

    Preset(String title, Personality personality) {
        this.title = title;
        this.personality = personality;
    }

    public String title() {
        return title;
    }

    public String word() {
        return title.toLowerCase(Locale.ROOT);
    }

    public Personality personality() {
        return personality;
    }

    public static Optional<Preset> named(String word) {
        return Arrays.stream(values()).filter(preset -> preset.word().equals(word.toLowerCase(Locale.ROOT))).findFirst();
    }

    public static String listed() {
        return Arrays.stream(values()).map(Preset::word).collect(Collectors.joining(", "));
    }

    @Override
    public Personality appliedTo(Personality own) {
        return personality;
    }
}
