package io.github.ythibos.edi.domain.personality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PersonalityChangeTest {

    @Test
    void namesAPresetOrScalesWithTheirLevels() {
        assertThat(PersonalityChange.parse(List.of("Plain"))).isEqualTo(Preset.PLAIN);
        assertThat(PersonalityChange.parse(List.of("Humour", "6", "formality", "8")))
                .isEqualTo(new PersonalityOverride(Map.of(Scale.HUMOUR, Level.of(6), Scale.FORMALITY, Level.of(8))));
    }

    @Test
    void refusesWhatIsNeitherAPresetNorAScaleWithItsLevel() {
        assertThatThrownBy(() -> PersonalityChange.parse(List.of()))
                .hasMessage("A personality change names a preset, such as balanced, or scales with their levels, such as humour 6 formality 8.");
        assertThatThrownBy(() -> PersonalityChange.parse(List.of("cheerful"))).hasMessage("cheerful is not a preset; the presets are balanced, plain, demo.");
        assertThatThrownBy(() -> PersonalityChange.parse(List.of("humor", "6")))
                .hasMessage("humor is not a personality scale; the scales are humour, formality, verbosity, warmth, directness, initiative, depth, enthusiasm,"
                        + " flavour.");
        assertThatThrownBy(() -> PersonalityChange.parse(List.of("humour", "lots"))).hasMessage("A personality level is a whole number from 0 to 10, not lots.");
        assertThatThrownBy(() -> PersonalityChange.parse(List.of("humour", "11"))).hasMessage("A personality level runs from 0 to 10, not 11.");
        assertThatThrownBy(() -> PersonalityChange.parse(List.of("humour", "2", "humour", "5"))).hasMessage("The change names humour twice; name each scale once.");
    }

    @Test
    void knowsNoChangeForTheSessionOnly() {
        assertThatThrownBy(() -> PersonalityChange.parse(List.of("humour", "8", "for", "now"))).hasMessageStartingWith("for is not a personality scale");
    }
}
