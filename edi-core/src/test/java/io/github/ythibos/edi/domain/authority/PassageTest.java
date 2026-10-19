package io.github.ythibos.edi.domain.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PassageTest {

    @Test
    void takesTheWholePromptAndEachOfItsLines() {
        assertThat(Passage.allTypedIn("D58 A\n\nAnd discard the lamp copy.\n"))
                .containsExactly(Passage.of("D58 A\n\nAnd discard the lamp copy."), Passage.of("D58 A"), Passage.of("And discard the lamp copy."));
    }

    @Test
    void comparesWordsWhateverTheirCaseSpacingAndQuotes() {
        assertThat(Passage.of("  Ship   the “lamp”\tplan’s steps "))
                .isEqualTo(Passage.of("ship the \"lamp\" plan's steps"));
        assertThat(Passage.of("Ship the lamp")).isNotEqualTo(Passage.of("Ship the lamps"));
    }

    @Test
    void holdsSomeWords() {
        assertThat(Passage.allTypedIn("   ")).isEmpty();
        assertThatThrownBy(() -> Passage.of(" \n ")).isInstanceOf(IllegalArgumentException.class);
    }
}
