package io.github.ythibos.edi.application.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.tracker.TicketWalk;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TrackerFixtures;

class WalkTicketTest {

    private static final WorkKey LAMPS = WorkKey.parse("SVC-40");

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void keepALamp() throws IOException {
        home = TrackerFixtures.laptopHome(directory);
    }

    @Test
    void walksATicketOfAJiraProjectToTheNamedStateThroughItsTracker() {
        TicketWalk.Arrived arrived = home.walkTicket().walk(LAMPS, "Testing");

        assertThat(arrived).isEqualTo(new TicketWalk.Arrived("To Do", List.of("In Progress", "In Review", "Testing")));
        assertThat(home.jira().statusOf("SVC-40")).isEqualTo("Testing");
    }

    @Test
    void refusesWithThePathTakenWhenAStepFails() {
        home.jira().stopsTheNextWalkWith(new TicketWalk.Stopped("To Do", List.of("In Progress"), "JIRA refused the transition of SVC-40 to In Review (HTTP 400)"));

        assertThatThrownBy(() -> home.walkTicket().walk(LAMPS, "Done")).isInstanceOf(Refusal.class)
                .hasMessage("SVC-40 stopped at In Progress after the steps To Do -> In Progress on its way to Done: "
                        + "JIRA refused the transition of SVC-40 to In Review (HTTP 400).");
    }

    @Test
    void refusesWithoutAStepWhenTheFirstOneFails() {
        home.jira().stopsTheNextWalkWith(new TicketWalk.Stopped("To Do", List.of(), "JIRA could not answer for the transitions of SVC-40 (HTTP 503)"));

        assertThatThrownBy(() -> home.walkTicket().walk(LAMPS, "Done")).isInstanceOf(Refusal.class)
                .hasMessage("SVC-40 stopped at To Do before taking a step on its way to Done: JIRA could not answer for the transitions of SVC-40 (HTTP 503).");
    }

    @Test
    void refusesAKeyNoJiraProjectOwns() {
        assertThatThrownBy(() -> home.walkTicket().walk(WorkKey.parse("HRN-3"), "Done")).isInstanceOf(Refusal.class)
                .hasMessage("No registered project books its work on JIRA keys HRN-<n>.");
    }
}
