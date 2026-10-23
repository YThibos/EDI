package io.github.ythibos.edi.domain.personality;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class AddressingTest {

    @Test
    void weighsTheAddressWordByContextAtTheHighestFormality() {
        Addressing bridge = Addressing.at(Level.of(10));

        assertThat(Arrays.stream(MessageContext.values()).map(bridge::in)).containsExactly(Likelihood.ALMOST_ALWAYS, Likelihood.ALMOST_ALWAYS,
                Likelihood.ALMOST_ALWAYS, Likelihood.USUALLY, Likelihood.SOMETIMES, Likelihood.SOMETIMES);
    }

    @Test
    void usesTheAddressWordLessOftenAtANeutralFormality() {
        Addressing neutral = Addressing.at(Level.of(5));

        assertThat(neutral.in(MessageContext.BAD_NEWS)).isEqualTo(Likelihood.USUALLY);
        assertThat(neutral.in(MessageContext.GOOD_NEWS)).isEqualTo(Likelihood.SOMETIMES);
        assertThat(neutral.in(MessageContext.NOTHING_TO_REPORT)).isEqualTo(Likelihood.RARELY);
    }

    @Test
    void neverUsesTheAddressWordAtFormalityZero() {
        Addressing casual = Addressing.at(Level.LOWEST);

        assertThat(Arrays.stream(MessageContext.values()).map(casual::in)).containsOnly(Likelihood.NEVER);
        assertThat(casual.isNever()).isTrue();
        assertThat(casual.places()).isEmpty();
        assertThat(casual.preferredPlace()).isEmpty();
    }

    @Test
    void neverReachesEveryStatementAtAnyFormality() {
        assertThat(IntStream.rangeClosed(0, 10).mapToObj(level -> Addressing.at(Level.of(level)).in(MessageContext.NOTHING_TO_REPORT)))
                .doesNotContain(Likelihood.USUALLY, Likelihood.ALMOST_ALWAYS);
    }

    @Test
    void placesTheAddressWordByFormality() {
        assertThat(Addressing.at(Level.of(8)).places()).containsExactly(AddressPlacement.OPENING_THE_MESSAGE, AddressPlacement.OPENING_A_LATER_SENTENCE);
        assertThat(Addressing.at(Level.of(8)).preferredPlace()).isEmpty();
        assertThat(Addressing.at(Level.of(7)).places()).containsExactly(AddressPlacement.OPENING_THE_MESSAGE, AddressPlacement.OPENING_A_LATER_SENTENCE,
                AddressPlacement.CLOSING_TAG);
        assertThat(Addressing.at(Level.of(7)).preferredPlace()).isEmpty();
        assertThat(Addressing.at(Level.of(3)).preferredPlace()).contains(AddressPlacement.CLOSING_TAG);
    }
}
