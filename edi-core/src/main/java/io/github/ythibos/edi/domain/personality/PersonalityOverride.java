package io.github.ythibos.edi.domain.personality;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record PersonalityOverride(Map<Scale, Level> levels) implements PersonalityChange {

    public static final PersonalityOverride SHIPPED_DEMO = new PersonalityOverride(Map.of(
            Scale.FORMALITY, Level.of(7),
            Scale.HUMOUR, Level.of(3),
            Scale.VERBOSITY, Level.of(5),
            Scale.FLAVOUR, Level.of(7)));

    public PersonalityOverride {
        EnumMap<Scale, Level> inScaleOrder = new EnumMap<>(Scale.class);
        inScaleOrder.putAll(levels);
        levels = Collections.unmodifiableMap(inScaleOrder);
    }

    @Override
    public Personality appliedTo(Personality own) {
        return own.overriddenBy(this);
    }
}
