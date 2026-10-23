package io.github.ythibos.edi.web.personality;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.application.personality.PersonalityInForce;
import io.github.ythibos.edi.domain.personality.CaptainsPersonality;
import io.github.ythibos.edi.domain.personality.Personality;
import io.github.ythibos.edi.domain.personality.PersonalityOverride;
import io.github.ythibos.edi.domain.status.CaptainAddress;

class PersonalityTextTest {

    private static final boolean NO_DEMO = false;
    private static final CaptainsPersonality BRIDGE = chosen(Personality.of(2, 10, 5, 6, 5, 7, 5, 6, 6));

    private final PersonalityText text = new PersonalityText();

    @Test
    void namesThePersonalityTheAddressWordAndTheBadNewsRail() {
        assertThat(text.text(new PersonalityInForce(CaptainAddress.DEFAULT, BRIDGE, NO_DEMO))).isEqualTo("""
                Personality: humour 2, formality 10, verbosity 5, warmth 6, directness 5, initiative 7, depth 5, enthusiasm 6, flavour 6; \
                edi-personality says what each level means.
                Address word "Commander": almost always with bad news, a decision needed or the session's first message; usually with good news; \
                sometimes with a status report or nothing to report; it goes at the start of the message or at the start of a later sentence.
                Bad news: humour 0, formality 10, verbosity 5, warmth 6, directness 7, initiative 7, depth 5, enthusiasm 0, flavour 3, \
                and never "Acknowledged".
                """);
    }

    @Test
    void offersThePresetsWhileThePersonalityIsBalancedByDefault() {
        assertThat(text.lines(new PersonalityInForce(new CaptainAddress("Skipper"), CaptainsPersonality.UNCHOSEN, NO_DEMO))).containsExactly(
                "Personality: humour 3, formality 5, verbosity 5, warmth 4, directness 6, initiative 5, depth 5, enthusiasm 3, flavour 5;"
                        + " edi-personality says what each level means.",
                "No personality is chosen yet, so Balanced applies: offer the captain the presets in this session (edi-personality).",
                "Address word \"Skipper\": usually with bad news, a decision needed or the session's first message; sometimes with good news;"
                        + " rarely with a status report or nothing to report; it goes at the start of the message, at the start of a later sentence"
                        + " or as a closing tag.",
                "Bad news: humour 0, formality 5, verbosity 5, warmth 4, directness 7, initiative 5, depth 5, enthusiasm 0, flavour 3,"
                        + " and never \"Acknowledged\".");
    }

    @Test
    void speaksWithTheDemoOverrideWhileADemoHolds() {
        assertThat(text.lines(new PersonalityInForce(CaptainAddress.DEFAULT, BRIDGE, true)).getFirst()).isEqualTo(
                "Personality for the demo: humour 3, formality 7, verbosity 5, warmth 6, directness 5, initiative 7, depth 5, enthusiasm 6, flavour 7;"
                        + " edi-personality says what each level means.");
    }

    @Test
    void prefersTheClosingTagAtALowFormalityAndDropsTheAddressWordAtZero() {
        assertThat(text.lines(new PersonalityInForce(CaptainAddress.DEFAULT, chosen(Personality.of(2, 2, 5, 6, 5, 7, 5, 6, 6)), NO_DEMO)))
                .contains("Address word \"Commander\": sometimes with bad news, a decision needed or the session's first message;"
                        + " rarely with good news, a status report or nothing to report;"
                        + " it goes preferably as a closing tag, else at the start of the message or at the start of a later sentence.");
        assertThat(text.lines(new PersonalityInForce(CaptainAddress.DEFAULT, chosen(Personality.of(2, 0, 5, 6, 5, 7, 5, 6, 6)), NO_DEMO)))
                .contains("Address word \"Commander\": never.");
    }

    private static CaptainsPersonality chosen(Personality personality) {
        return new CaptainsPersonality(Optional.of(personality), PersonalityOverride.SHIPPED_DEMO);
    }
}
