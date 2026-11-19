package io.github.ythibos.edi.domain.primary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PrimaryOutputStyleTest {

    @Test
    void carriesThePromptUnderItsNameKeepingClaudeCodesCodingInstructions() {
        assertThat(PrimaryOutputStyle.carrying("# Anchor\n\nHold the line.\n")).isEqualTo("""
                ---
                name: edi-primary
                description: The primary's anchor and the captain's memory, written by edi up
                keep-coding-instructions: true
                ---

                # Anchor

                Hold the line.
                """);
    }

    @Test
    void holdsTheCurrentAnchorOnlyWhenItCarriesTheWholeOfIt() {
        String style = PrimaryOutputStyle.carrying("# Anchor\n\nHold the line.\n\n# The captain's memory\n");

        assertThat(PrimaryOutputStyle.anchorHeldIn(style, "# Anchor\n\nHold the line.\n")).isEqualTo(HeldAnchor.CURRENT);
        assertThat(PrimaryOutputStyle.anchorHeldIn(style, "# Anchor\n\nHold the new line.\n")).isEqualTo(HeldAnchor.OLDER);
        assertThat(PrimaryOutputStyle.anchorHeldIn(style, "# Renamed anchor\n\nHold the line.\n")).isEqualTo(HeldAnchor.UNWRITTEN);
    }

    @Test
    void takesTheAnchorsFirstLineAsItsHeading() {
        assertThat(PrimaryOutputStyle.headingOf("# Anchor\n\nHold the line.\n")).isEqualTo("# Anchor");
    }
}
