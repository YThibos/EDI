package io.github.ythibos.edi.domain.personality;

import java.util.Objects;
import java.util.Optional;

public record CaptainsPersonality(Optional<Personality> chosen, PersonalityOverride demo) {

    public static final CaptainsPersonality UNCHOSEN = new CaptainsPersonality(Optional.empty(), PersonalityOverride.SHIPPED_DEMO);

    public CaptainsPersonality {
        Objects.requireNonNull(chosen, "The captain's personality says whether one is chosen.");
        Objects.requireNonNull(demo, "The captain's personality carries the demo's override.");
    }

    public Personality own() {
        return chosen.orElse(Preset.BALANCED.personality());
    }

    public boolean isChosen() {
        return chosen.isPresent();
    }

    public Personality speaking(boolean demoHolds) {
        return demoHolds ? own().overriddenBy(demo) : own();
    }

    public CaptainsPersonality changedBy(PersonalityChange change) {
        return new CaptainsPersonality(Optional.of(change.appliedTo(own())), demo);
    }
}
