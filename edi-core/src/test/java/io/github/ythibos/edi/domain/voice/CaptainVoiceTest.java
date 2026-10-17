package io.github.ythibos.edi.domain.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.Refusal;

class CaptainVoiceTest {

    @Test
    void findsInternalLabelsAsWholeWordsWhateverTheirCase() {
        assertThat(CaptainVoice.internalLabelsIn("The Worktree is gone and its crewmates wait.")).extracting(InternalLabel::label)
                .containsExactly("worktree", "crewmates");
        assertThat(CaptainVoice.internalLabelsIn("The check fails closed.")).extracting(InternalLabel::label).containsExactly("fails closed");
        assertThat(CaptainVoice.internalLabelsIn("A wake-up call, awake and on the worktrees-to-be.")).isEmpty();
    }

    @Test
    void leavesQuotedNamesCodePathsAndLinksAsTheyAre() {
        assertThat(CaptainVoice.internalLabelsIn("""
                The task "Fix the worktree cleanup" waits; `edi worktree discard` frees /home/example/.edi/worktrees/lamp,
                and https://example.org/docs/wake explains it.""")).isEmpty();
    }

    @Test
    void refusesCaptainFacingTextWithAnInternalLabel() {
        assertThatThrownBy(() -> CaptainVoice.requireOutcomes("The turn's wording", "The teardown finished."))
                .isInstanceOf(Refusal.class)
                .hasMessageStartingWith("The turn's wording speaks to the captain in outcomes: say \"cleanup\" instead of \"teardown\"");
        CaptainVoice.requireOutcomes("The turn's wording", "The cleanup finished.");
    }
}
