package io.github.ythibos.edi.domain.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class DeliveryNoteTest {

    @Test
    void readsTheStepsThatRemainAfterTheMergeAsTheItemsOfTheirSection() {
        DeliveryNote note = DeliveryNote.of("""
                # Delivery note

                ## What changed

                - The lamp is fitted.

                ## Steps that remain after the merge

                - Run `edi up` so the core loads the new store.
                - Watch the next two merges and confirm the cancelled jobs,
                  then close the follow-up.
                1. Tell the crew.

                worked: 2026-11-18T11:00 -> 2026-11-18T11:11 = 0h11m
                """);

        assertThat(note.stepsAfterLanding()).containsExactly("Run `edi up` so the core loads the new store.",
                "Watch the next two merges and confirm the cancelled jobs, then close the follow-up.", "Tell the crew.");
        assertThat(note.landingOrder()).isNull();
    }

    @Test
    void readsTheSentencesOfASectionWithoutAListAndStopsAtTheNextSectionOfItsLevel() {
        DeliveryNote note = DeliveryNote.of("""
                ## After the merge

                Run the migration.
                Restart the core.

                ### On the desktop

                - Restart there too.

                ## Merge order

                Merges after #329 (V34).

                ## Evidence

                - Tests pass.
                worked: 2026-11-18T11:00 -> 2026-11-18T11:11 = 0h11m
                """);

        assertThat(note.stepsAfterLanding()).containsExactly("Run the migration.", "Restart the core.", "Restart there too.");
        assertThat(note.landingOrder()).isEqualTo("Merges after #329 (V34).");
    }

    @Test
    void readsAFencedCommentAsAStepRatherThanAsAHeadingThatEndsTheSection() {
        DeliveryNote note = DeliveryNote.of("""
                ## Steps that remain after the merge

                - Restart the core:

                ```bash
                # restart the core
                edi up
                ```

                - Tell the crew.

                ## Evidence

                - Tests pass.
                """);

        assertThat(note.stepsAfterLanding()).containsExactly("Restart the core:", "# restart the core", "edi up", "Tell the crew.");
    }

    @Test
    void findsNothingInANoteWithoutEitherSection() {
        DeliveryNote note = DeliveryNote.of("# Delivery note\n\n## What changed\n\n- The lamp is fitted.\n\nworked: 2026-11-18T11:00 -> 2026-11-18T11:11 = 0h11m\n");

        assertThat(note).isEqualTo(new DeliveryNote(List.of(), null));
    }
}
