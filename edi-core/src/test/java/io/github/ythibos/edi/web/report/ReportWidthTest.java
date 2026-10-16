package io.github.ythibos.edi.web.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReportWidthTest {

    @ParameterizedTest
    @CsvSource({"90, 88", "122, 120", "121, 119", "62, 60", "40, 60", "0, 60", "318, 120"})
    void narrowsToThePaneLessClaudeCodesIndentWithinItsBounds(int paneWidth, int columns) {
        assertThat(ReportWidth.forPane(paneWidth)).isEqualTo(new ReportWidth(columns));
    }

    @Test
    void isOneHundredAndTwentyColumnsOutsideAPane() {
        assertThat(ReportWidth.standard().columns()).isEqualTo(120);
    }

    @Test
    void neverNarrowsBelowSixtyColumns() {
        assertThatThrownBy(() -> new ReportWidth(59)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The captain report is at least 60 columns wide, not 59.");
    }
}
