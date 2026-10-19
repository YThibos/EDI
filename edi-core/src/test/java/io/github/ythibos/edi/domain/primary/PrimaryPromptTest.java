package io.github.ythibos.edi.domain.primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.memory.MemoryFile;

class PrimaryPromptTest {

    @Test
    void composesTheAnchorItsAddendumAndTheMemoryLoadedAtStart() {
        String prompt = PrimaryPrompt.composed("# Anchor\n", "Personal addendum.\n",
                List.of(new MemoryFile("profile.md", "# Profile\n"), new MemoryFile("machine.md", ""), new MemoryFile("learnings.md", "# Learnings\n")));

        assertThat(prompt).isEqualTo("""
                # Anchor

                Personal addendum.

                # The captain's memory, loaded at session start

                <!-- memory/profile.md -->
                # Profile

                <!-- memory/learnings.md -->
                # Learnings
                """);
    }

    @Test
    void leavesOutWhatIsMissing() {
        assertThat(PrimaryPrompt.composed("# Anchor", "", List.of(new MemoryFile("profile.md", " ")))).isEqualTo("# Anchor\n");
    }

    @Test
    void refusesAnAnchorAndAddendumAboveTenThousandBytes() {
        String anchor = "a".repeat(9_000);

        assertThat(PrimaryPrompt.composed(anchor, "é".repeat(500), List.of())).startsWith(anchor);
        assertThatThrownBy(() -> PrimaryPrompt.composed(anchor, "é".repeat(501), List.of()))
                .isInstanceOf(Refusal.class)
                .hasMessageContaining("hold 10002 bytes, above the 10000 the anchor may load (I-35)");
    }
}
