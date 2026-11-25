package io.github.ythibos.edi.domain.tracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class WorkflowGraphTest {

    private static final WorkflowGraph CHAIN = WorkflowGraph.of(Map.of(
            "Open", List.of("Doing"),
            "Doing", List.of("Review", "Open"),
            "Review", List.of("Done", "Doing"),
            "Done", List.of("Open")));

    @Test
    void findsTheShortestPathAndNamesEachStatusAfterTheStart() {
        assertThat(CHAIN.pathBetween("Open", "Done")).contains(List.of("Doing", "Review", "Done"));
        assertThat(CHAIN.pathBetween("Done", "Review")).contains(List.of("Open", "Doing", "Review"));
    }

    @Test
    void comparesStatusesWithoutRegardToCase() {
        assertThat(CHAIN.pathBetween("open", "DONE")).contains(List.of("Doing", "Review", "Done"));
        assertThat(CHAIN.hasSeen("review")).isTrue();
    }

    @Test
    void findsNoPathToAStatusNothingLeadsTo() {
        assertThat(CHAIN.pathBetween("Open", "Archived")).isEmpty();
        assertThat(CHAIN.pathBetween("Open", "Open")).isEmpty();
    }

    @Test
    void prefersTheShorterOfTwoPaths() {
        WorkflowGraph shortcut = CHAIN.learning("Doing", List.of("Review", "Open", "Done"));

        assertThat(shortcut.pathBetween("Open", "Done")).contains(List.of("Doing", "Done"));
    }

    @Test
    void replacesWhatAStatusOffersWhenItIsSeenAgain() {
        WorkflowGraph narrowed = CHAIN.learning("Review", List.of("Doing"));

        assertThat(narrowed.offeredFrom("Review")).containsExactly("Doing");
        assertThat(narrowed.pathBetween("Open", "Done")).isEmpty();
        assertThat(CHAIN.offeredFrom("Review")).containsExactlyInAnyOrder("Done", "Doing");
    }

    @Test
    void headsForTheNearestStatusNeverSeen() {
        WorkflowGraph partial = WorkflowGraph.unknown()
                .learning("Open", List.of("Cancelled", "Doing"))
                .learning("Doing", List.of("Review"));

        assertThat(partial.pathToNearestUnseen("Open", status -> 0)).contains(List.of("Cancelled"));
        assertThat(partial.pathToNearestUnseen("Doing", status -> 0)).contains(List.of("Review"));
    }

    @Test
    void breaksATieBetweenNearUnseenStatusesByTheirCloseness() {
        WorkflowGraph partial = WorkflowGraph.unknown().learning("Open", List.of("Cancelled", "Doing"));

        assertThat(partial.pathToNearestUnseen("Open", status -> status.equals("Doing") ? 1 : 5)).contains(List.of("Doing"));
        assertThat(partial.pathToNearestUnseen("Open", status -> status.equals("Cancelled") ? 1 : 5)).contains(List.of("Cancelled"));
    }

    @Test
    void knowsAStatusSeenWithNothingOfferedAsSeenAllTheSame() {
        WorkflowGraph deadEnd = WorkflowGraph.unknown().learning("Open", List.of("Cancelled")).learning("Cancelled", List.of());

        assertThat(deadEnd.hasSeen("Cancelled")).isTrue();
        assertThat(deadEnd.pathToNearestUnseen("Open", status -> 0)).isEmpty();
    }
}
