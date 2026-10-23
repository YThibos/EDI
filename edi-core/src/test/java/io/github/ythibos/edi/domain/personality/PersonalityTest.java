package io.github.ythibos.edi.domain.personality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class PersonalityTest {

    private static final Personality BRIDGE = Personality.of(2, 10, 5, 6, 5, 7, 5, 6, 6);

    @Test
    void setsEveryScaleFromZeroToTen() {
        assertThat(BRIDGE.levelOf(Scale.FORMALITY)).isEqualTo(Level.of(10));
        assertThatThrownBy(() -> Level.of(11)).hasMessage("A personality level runs from 0 to 10, not 11.");
        assertThatThrownBy(() -> new Personality(Map.of(Scale.HUMOUR, Level.of(2))))
                .hasMessage("A personality sets every scale; this one lacks formality, verbosity, warmth, directness, initiative, depth, enthusiasm, flavour.");
    }

    @Test
    void bringsBadNewsWithoutHumourOrEnthusiasmAndWithDirectness() {
        assertThat(BRIDGE.forBadNews()).isEqualTo(Personality.of(0, 10, 5, 6, 7, 7, 5, 0, 3));
        assertThat(Personality.of(9, 4, 1, 8, 9, 2, 6, 10, 2).forBadNews()).isEqualTo(Personality.of(0, 4, 3, 8, 9, 2, 6, 0, 2));
        assertThat(Preset.PLAIN.personality().forBadNews()).isEqualTo(Preset.PLAIN.personality());
    }

    @Test
    void takesTheLevelsAnOverrideNamesAndKeepsTheRest() {
        assertThat(BRIDGE.overriddenBy(PersonalityOverride.SHIPPED_DEMO)).isEqualTo(Personality.of(3, 7, 5, 6, 5, 7, 5, 6, 7));
    }

    @Test
    void speaksBalancedUntilTheCaptainChoosesAndOverridesItForADemo() {
        CaptainsPersonality chosen = CaptainsPersonality.UNCHOSEN.changedBy(PersonalityChange.parse(List.of("humour", "6")));

        assertThat(CaptainsPersonality.UNCHOSEN.isChosen()).isFalse();
        assertThat(CaptainsPersonality.UNCHOSEN.speaking(false)).isEqualTo(Preset.BALANCED.personality());
        assertThat(chosen).isEqualTo(new CaptainsPersonality(Optional.of(Personality.of(6, 5, 5, 4, 6, 5, 5, 3, 5)), PersonalityOverride.SHIPPED_DEMO));
        assertThat(chosen.speaking(true)).isEqualTo(Personality.of(3, 7, 5, 4, 6, 5, 5, 3, 7));
        assertThat(chosen.changedBy(Preset.PLAIN).own()).isEqualTo(Preset.PLAIN.personality());
    }
}
