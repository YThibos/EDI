package io.github.ythibos.edi.domain.tracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class WorkflowGraphTest {

    private static final Workflow FORWARD = new Workflow(List.of("Open", "Doing", "Review", "Done"));

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
        WorkflowGraph shortcut = WorkflowGraph.of(Map.of(
                "Open", List.of("Doing"),
                "Doing", List.of("Review", "Open", "Done"),
                "Review", List.of("Done", "Doing")));

        assertThat(shortcut.pathBetween("Open", "Done")).contains(List.of("Doing", "Done"));
    }

    @Test
    void headsForTheNearestStatusNeverSeen() {
        WorkflowGraph partial = WorkflowGraph.of(Map.of(
                "Open", List.of("Doing"),
                "Doing", List.of("Review", "Done")));

        assertThat(partial.pathToNearestUnseen("Open", FORWARD, "Done")).contains(List.of("Doing", "Done"));
        assertThat(partial.pathToNearestUnseen("Doing", FORWARD, "Review")).contains(List.of("Review"));
    }

    @Test
    void exploresAStatusOutsideTheWorkflowOnlyAfterEveryReachableOneInsideIt() {
        WorkflowGraph partial = WorkflowGraph.of(Map.of(
                "Open", List.of("Cancelled", "Doing"),
                "Doing", List.of("Review")));
        WorkflowGraph cornered = WorkflowGraph.of(Map.of("Open", List.of("Cancelled")));

        assertThat(partial.pathToNearestUnseen("Open", FORWARD, "Done")).contains(List.of("Doing", "Review"));
        assertThat(cornered.pathToNearestUnseen("Open", FORWARD, "Done")).contains(List.of("Cancelled"));
    }

    @Test
    void breaksATieBetweenNearUnseenStatusesByTheirClosenessToTheTarget() {
        WorkflowGraph partial = WorkflowGraph.of(Map.of("Open", List.of("Review", "Doing")));

        assertThat(partial.pathToNearestUnseen("Open", FORWARD, "Doing")).contains(List.of("Doing"));
        assertThat(partial.pathToNearestUnseen("Open", FORWARD, "Done")).contains(List.of("Review"));
    }

    @Test
    void knowsAStatusSeenWithNothingOfferedAsSeenAllTheSame() {
        WorkflowGraph deadEnd = WorkflowGraph.of(Map.of("Open", List.of("Cancelled"), "Cancelled", List.of()));

        assertThat(deadEnd.hasSeen("Cancelled")).isTrue();
        assertThat(deadEnd.pathToNearestUnseen("Open", FORWARD, "Done")).isEmpty();
    }
}
