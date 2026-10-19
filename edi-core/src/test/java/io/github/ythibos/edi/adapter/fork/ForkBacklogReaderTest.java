package io.github.ythibos.edi.adapter.fork;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class ForkBacklogReaderTest {

    private final ForkBacklogReader reader = new ForkBacklogReader(new JsonMapper());

    @Test
    void readsTheEntriesOfEachSectionWithTheirBodies() {
        ForkBacklog backlog = reader.read("""
                # Backlog

                ## In flight
                - **beacon** - Light the beacon (repo: lighthouse) (since 2026-10-01)
                  First line.

                  Second paragraph.
                    indented further

                - [ ] lantern - Rework the lantern (repo: lighthouse) (since 2026-10-02)
                ## Queued
                - [ ] horn - Tune the horn (repo: lighthouse) (since 2026-10-03)
                ## Done (recent)
                - [x] pole - Replace the pole (repo: lighthouse) (done 2026-09-30)
                """);

        assertThat(backlog.entries()).extracting(ForkEntry::id, ForkEntry::section, ForkEntry::line).containsExactly(
                tuple("beacon", ForkSection.IN_FLIGHT, 4),
                tuple("lantern", ForkSection.IN_FLIGHT, 10),
                tuple("horn", ForkSection.QUEUED, 12),
                tuple("pole", ForkSection.DONE, 14));
        assertThat(backlog.entries().getFirst().body()).containsExactly("First line.", "", "Second paragraph.", "  indented further");
        assertThat(backlog.strayLines()).isEmpty();
    }

    @Test
    void readsOnlyTheBulletsOfItsOwnSection() {
        ForkBacklog backlog = reader.read("""
                ## Queued
                - [x] checked - A checked entry in the queue
                ## Done
                - [ ] open - An open entry among the done
                """);

        assertThat(backlog.entries()).isEmpty();
        assertThat(backlog.strayLines()).extracting(StrayLine::line).containsExactly(2, 4);
    }

    @Test
    void setsAsideTheEntriesOfAnyOtherSection() {
        ForkBacklog backlog = reader.read("""
                - [ ] before-any-section - ignored
                ## Captain's Call
                - [x] old-call - RESOLVED: kept.
                Some prose.
                ## Queued
                - [ ] horn - Tune the horn
                """);

        assertThat(backlog.setAside()).containsExactly(new SetAsideEntry(3, "Captain's Call", "old-call"));
        assertThat(backlog.entries()).extracting(ForkEntry::id).containsExactly("horn");
    }

    @Test
    void readsADecisionWithItsCardAndRecordedAnswer() {
        ForkBacklog backlog = reader.read("""
                ## Done
                - [x] lamp - Choose the lamp (kind: captain) (done 2026-10-05)
                  Captain hold set: 2026-10-01T08:00:00Z

                  Captain decision:
                  Captain (2026-10-05): "D55, A"
                  Answer: A - the LED lamp.

                  Captain decision id: D55
                  Captain decision card: {"title":"Which lamp?","context":"","projects":["lighthouse"],"options":[{"key":"A","text":"LED"}],"recommend":{"key":"A","why":"lasts"}}
                """);

        ForkEntry lamp = backlog.entries().getFirst();
        assertThat(lamp.holdSet()).isEqualTo(Instant.parse("2026-10-01T08:00:00Z"));
        assertThat(lamp.decision().number()).isEqualTo(55);
        assertThat(lamp.decision().card()).isEqualTo(new ForkDecisionCard("Which lamp?", "", List.of("lighthouse"),
                List.of(new ForkOption("A", "LED")), new ForkRecommendation("A", "lasts")));
        assertThat(lamp.decision().recordedAnswer()).containsExactly("Captain (2026-10-05): \"D55, A\"", "Answer: A - the LED lamp.");
    }

    @Test
    void notesACardItCannotRead() {
        ForkBacklog backlog = reader.read("""
                ## Queued
                - [ ] lamp - Choose the lamp
                  Captain decision id: D9
                  Captain decision card: {"title": unquoted}
                """);

        ForkDecision decision = backlog.entries().getFirst().decision();
        assertThat(decision.number()).isEqualTo(9);
        assertThat(decision.card()).isNull();
        assertThat(decision.unreadableCard()).isNotBlank();
    }
}
