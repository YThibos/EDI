package io.github.ythibos.edi.web.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TableTest {

    @Test
    void alignsTheRowsAndLeavesFreeLinesAsTheyAre() {
        String text = new Table("  ")
                .line("Heading that is longer than any cell")
                .row("a", "kind", "last cell is never padded")
                .row("longer", "k", "x")
                .line("    a detail line")
                .row("b", "", "")
                .text();

        assertThat(text).isEqualTo("""
                Heading that is longer than any cell
                  a       kind  last cell is never padded
                  longer  k     x
                    a detail line
                  b
                """);
    }

    @Test
    void speaksOfEventTypesInWords() {
        assertThat(Words.ofType("DecisionAnswered")).isEqualTo("decision answered");
        assertThat(Words.counted("Queued", 3)).isEqualTo("Queued (3)");
        assertThat(Words.indented("one\n\ntwo", "  ")).isEqualTo("  one\n\n  two");
    }
}
