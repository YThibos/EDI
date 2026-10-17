package io.github.ythibos.edi.application.tracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.tracker.TrackerUnavailable;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TrackerFixtures;
import io.github.ythibos.edi.testing.WorkEvents;
import io.github.ythibos.edi.testing.WorkerFixtures;

class FollowTicketsTest {

    private static final TaskId LAMPS = new TaskId("fix-lamps");

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void storeTrackedWork() throws IOException {
        home = TrackerFixtures.laptopHome(directory);
        home.storing(List.of(WorkerFixtures.queuedShip("fix-lamps", "service", "SVC-40"), WorkerFixtures.queuedShip("plan-week", "service", "SVC-1"),
                WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));
    }

    @Test
    void placesTheTicketInTheSprintLinksTheDraftAndMovesTheTicketAfterTheMerge() {
        WorkEvents.of(home, "fix-lamps").dispatchedTo(1, "feature/SVC-40");
        home.eventFollowers().followTheLog();
        assertThat(home.jira().sprintOf("SVC-40")).isEqualTo("Sprint 7");

        WorkEvents.of(home, "fix-lamps").deliveredAsADraft();
        home.eventFollowers().followTheLog();
        assertThat(home.jira().linksOf("SVC-40")).containsExactly(WorkEvents.DRAFT.url());

        WorkEvents.of(home, "fix-lamps").merged();
        home.eventFollowers().followTheLog();
        assertThat(home.jira().statusOf("SVC-40")).isEqualTo("Testing");

        assertThat(home.eventTypesOf(LAMPS)).containsSubsequence("TicketAddedToSprint", "TicketLinked", "TicketTransitioned");
    }

    @Test
    void neverTouchesAnEvergreenTicketOrAnEdiNativeKey() {
        WorkEvents.of(home, "plan-week").dispatchedTo(1, "chore/SVC-1").deliveredAsADraft().merged();
        WorkEvents.of(home, "fit-lamp").dispatchedTo(1, "feature/HRN-3").deliveredAsADraft().merged();

        home.eventFollowers().followTheLog();

        assertThat(home.jira().sprintOf("SVC-1")).isNull();
        assertThat(home.jira().statusOf("SVC-1")).isEqualTo("In Progress");
        assertThat(home.eventTypesOf(new TaskId("plan-week"))).noneMatch(type -> type.startsWith("Ticket"));
        assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).noneMatch(type -> type.startsWith("Ticket"));
    }

    @Test
    void placesTheTicketOnlyAtTheTasksFirstDispatch() {
        WorkEvents.of(home, "fix-lamps").dispatchedTo(2, "feature/SVC-40");

        home.eventFollowers().followTheLog();

        assertThat(home.jira().sprintOf("SVC-40")).isNull();
    }

    @Test
    void wakesThePrimaryWhenTheTrackerRefusesAnUpdate() {
        home.jira().failsNextWith(new Refusal("several SVC sprints are active (Sprint 7, Hotfixes), so EDI adds SVC-40 to none of them"));
        WorkEvents.of(home, "fix-lamps").dispatchedTo(1, "feature/SVC-40");

        home.eventFollowers().followTheLog();

        assertThat(home.eventTypesOf(LAMPS)).endsWith("TicketUpdateRefused");
        assertThat(home.wakes().unacknowledged()).singleElement().satisfies(queued -> {
            assertThat(queued.wake().kind()).isEqualTo(WakeKind.TICKET_REFUSED);
            assertThat(queued.wake().detail()).isEqualTo("the sprint placement of SVC-40: several SVC sprints are active (Sprint 7, Hotfixes), so EDI"
                    + " adds SVC-40 to none of them");
        });
    }

    @Test
    void triesAgainInItsNextRoundWhenTheTrackerCannotBeReached() {
        home.jira().failsNextWith(new TrackerUnavailable("JIRA at tracker.example.org cannot be reached for the sprint of SVC-40: ConnectException."));
        WorkEvents.of(home, "fix-lamps").dispatchedTo(1, "feature/SVC-40");

        home.eventFollowers().followTheLog();
        assertThat(home.jira().sprintOf("SVC-40")).isNull();
        assertThat(home.eventTypesOf(LAMPS)).doesNotContain("TicketUpdateRefused");

        home.eventFollowers().followTheLog();
        assertThat(home.jira().sprintOf("SVC-40")).isEqualTo("Sprint 7");
    }
}
