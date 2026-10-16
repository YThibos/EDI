package io.github.ythibos.edi.adapter.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.ythibos.edi.domain.worker.PaneState;

class TmuxPaneProbeTest {

    private final TmuxPaneProbe probe = new TmuxPaneProbe();

    @ParameterizedTest
    @CsvSource({
        "trust-dialog.txt, DIALOG_TRUST",
        "idle-fresh-session.txt, IDLE_PROMPT",
        "busy-processing.txt, BUSY",
        "busy-running-a-command.txt, BUSY",
        "idle-after-a-turn.txt, IDLE_PROMPT",
        "idle-with-a-background-shell.txt, IDLE_PROMPT",
        "idle-named-session.txt, IDLE_PROMPT",
        "composer-holding-text.txt, UNKNOWN",
        "exit-picker.txt, DIALOG_EXIT_PICKER",
        "permission-dialog.txt, DIALOG_PERMISSION",
        "limit-wait-dialog.txt, DIALOG_LIMIT_WAIT",
        "spend-cap-dialog.txt, DIALOG_SPEND_CAP",
        "continue-after-reset.txt, DIALOG_CONTINUE_AFTER_RESET"})
    void readsTheStateOfEachCapturedPane(String fixture, PaneState state) throws IOException {
        assertThat(probe.classify(screen(fixture))).isEqualTo(state);
    }

    @Test
    void seesALineStillHeldInTheComposer() throws IOException {
        assertThat(probe.holdsInTheComposer(screen("composer-holding-text.txt"), "/exit")).isTrue();
        assertThat(probe.holdsInTheComposer(screen("composer-holding-text.txt"), "/context")).isFalse();
        assertThat(probe.holdsInTheComposer(screen("idle-named-session.txt"), "/exit")).isFalse();
    }

    @Test
    void readsAnEmptyOrForeignPaneAsUnknown() {
        assertThat(probe.classify(List.of())).isEqualTo(PaneState.UNKNOWN);
        assertThat(probe.classify(List.of("$ ls", "build.log  pom.xml", "$ "))).isEqualTo(PaneState.UNKNOWN);
    }

    @Test
    void readsOnlyTheLastThirtyLinesOfThePane() throws IOException {
        List<String> trustThenFortyLinesOfOutput = new ArrayList<>(screen("trust-dialog.txt"));
        IntStream.range(0, 40).forEach(line -> trustThenFortyLinesOfOutput.add("output line " + line));

        assertThat(probe.classify(trustThenFortyLinesOfOutput)).isEqualTo(PaneState.UNKNOWN);
    }

    static List<String> screen(String fixture) throws IOException {
        try (InputStream content = TmuxPaneProbeTest.class.getResourceAsStream("/pane-probe/" + fixture)) {
            return new String(content.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }
}
