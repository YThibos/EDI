package io.github.ythibos.edi.adapter.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.store.WorkflowCache;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.tracker.TicketWalk;
import io.github.ythibos.edi.testing.StubJiraServer;
import io.github.ythibos.edi.testing.StubJiraServer.Exchange;
import io.github.ythibos.edi.testing.TestHome;
import tools.jackson.databind.json.JsonMapper;

@Tag("adapter")
class JiraWalkTest {

    private static final String TOKEN = "jira-token-for-the-stub-only";
    private static final WorkKey BELL = WorkKey.parse("SVC-41");
    private static final WorkKey LAMP = WorkKey.parse("SVC-40");
    private static final List<String> NINE_STEPS = List.of("Reopened", "Backlog", "Selected", "Doing", "Peer Review", "Merged", "On Test", "Verified",
            "Ready For Prd");

    @TempDir
    private Path home;

    @TempDir
    private Path storeHome;

    private StubJiraServer jira;
    private JiraCalls calls;
    private WorkflowCache cache;
    private JiraTracker tracker;

    @BeforeEach
    void connectToAStubWhoseWorkflowTakesNineStepsBackToProduction() throws IOException {
        jira = StubJiraServer.acceptingToken(TOKEN)
                .holding("SVC-41", "Harbour bells", "Done")
                .holding("SVC-40", "Harbour lamps", "Done")
                .offeringFrom("Done", "Reopened")
                .offeringFrom("Reopened", "Backlog")
                .offeringFrom("Backlog", "Selected", "Cancelled")
                .offeringFrom("Cancelled")
                .offeringFrom("Selected", "Doing")
                .offeringFrom("Doing", "Peer Review")
                .offeringFrom("Peer Review", "Merged", "Doing")
                .offeringFrom("Merged", "On Test")
                .offeringFrom("On Test", "Verified")
                .offeringFrom("Verified", "Ready For Prd")
                .offeringFrom("Ready For Prd", "Done");
        calls = new JiraCalls(new JsonMapper());
        TestHome store = TestHome.in(storeHome);
        cache = new WorkflowCache(store.jdbc(), store.transaction());
        tracker = trackerFollowing("[Reopened, Backlog, Selected, Doing, Peer Review, Merged, On Test, Verified, Ready For Prd, Done]");
    }

    @AfterEach
    void disconnect() {
        calls.close();
        jira.close();
    }

    @Test
    void walksATicketFromDoneBackToReadyForPrdInNineSteps() {
        TicketWalk walk = tracker.walk(BELL, "Ready For Prd");

        assertThat(walk).isEqualTo(new TicketWalk.Arrived("Done", NINE_STEPS));
        assertThat(jira.statusOf("SVC-41")).isEqualTo("Ready For Prd");
        assertThat(transitionsTaken()).hasSize(9);
    }

    @Test
    void takesTheCachedPathWithoutExploringWhenAnotherTicketMakesTheSameWalk() {
        tracker.walk(BELL, "Ready For Prd");
        int exploredAndWalked = transitionsTaken().size();

        TicketWalk second = tracker.walk(LAMP, "Ready For Prd");

        assertThat(second).isEqualTo(new TicketWalk.Arrived("Done", NINE_STEPS));
        assertThat(transitionsTaken()).hasSize(exploredAndWalked + 9);
    }

    @Test
    void findsTheShortestPathWhenTheCacheHoldsSeveral() {
        jira.offeringFrom("Peer Review", "Merged", "Doing", "Verified");

        TicketWalk walk = tracker.walk(BELL, "Ready For Prd");

        assertThat(walk.through()).containsExactly("Reopened", "Backlog", "Selected", "Doing", "Peer Review", "Verified", "Ready For Prd");
    }

    @Test
    void passesByAStatusOutsideTheWorkflowWhenItExploresBeyondTheLearntPath() {
        tracker.walk(BELL, "Merged");

        TicketWalk walk = tracker.walk(LAMP, "Ready For Prd");

        assertThat(walk).isEqualTo(new TicketWalk.Arrived("Done", NINE_STEPS));
        assertThat(jira.statusOf("SVC-40")).isEqualTo("Ready For Prd");
    }

    @Test
    void leavesATicketAlreadyInTheStateWhereItIs() {
        assertThat(tracker.walk(BELL, "Done")).isEqualTo(new TicketWalk.Arrived("Done", List.of()));
        assertThat(transitionsTaken()).isEmpty();
    }

    @Test
    void stopsWithThePathTakenWhenAStepFails() {
        jira.refusingTransitionsTo("Merged");

        TicketWalk walk = tracker.walk(BELL, "Ready For Prd");

        assertThat(walk).isInstanceOfSatisfying(TicketWalk.Stopped.class, stopped -> {
            assertThat(stopped.from()).isEqualTo("Done");
            assertThat(stopped.through()).containsExactly("Reopened", "Backlog", "Selected", "Doing", "Peer Review");
            assertThat(stopped.because()).contains("JIRA refused the transition of SVC-41 to Merged (HTTP 400)", "Transition Merged is not valid for this issue");
        });
        assertThat(jira.statusOf("SVC-41")).isEqualTo("Peer Review");
    }

    @Test
    void stopsWhereTheTrackerOffersNoWayOnAndSaysWhichStatusesItHasSeen() {
        jira.offeringFrom("Verified");

        TicketWalk walk = tracker.walk(BELL, "Ready For Prd");

        assertThat(walk).isInstanceOfSatisfying(TicketWalk.Stopped.class, stopped -> {
            assertThat(stopped.lastStatus()).isEqualTo("Verified");
            assertThat(stopped.because()).startsWith("no transition path from Verified to Ready For Prd is known, and no unexplored status is reachable");
        });
    }

    @Test
    void stopsAtItsStepLimitWithoutTakingAStepBeyondIt() throws IOException {
        JiraTracker shortProfile = trackerFollowing("[Selected, Merged, Ready For Prd, Done]");

        TicketWalk walk = shortProfile.walk(BELL, "Ready For Prd");

        assertThat(walk).isInstanceOfSatisfying(TicketWalk.Stopped.class, stopped -> {
            assertThat(stopped.through()).containsExactlyElementsOf(NINE_STEPS.subList(0, 8));
            assertThat(stopped.because()).isEqualTo("it took 8 steps without arriving at Ready For Prd");
        });
        assertThat(jira.statusOf("SVC-41")).isEqualTo("Verified");
        assertThat(transitionsTaken()).hasSize(8);
    }

    @Test
    void refusesATargetThatIsNotAStatusOfTheWorkflowBeforeCallingAnything() {
        assertThatThrownBy(() -> tracker.walk(BELL, "Shipped")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Shipped is not a status of the workflow");
        assertThat(jira.exchanges()).isEmpty();
    }

    private JiraTracker trackerFollowing(String workflow) throws IOException {
        Path profileHome = Files.createTempDirectory(home, "home");
        Path keys = Files.writeString(profileHome.resolve("keys.yml"), "TRACKER:\n  domain: tracker.example.org\n  API-key: " + TOKEN + "\n");
        Files.createDirectories(profileHome.resolve("config"));
        Files.writeString(profileHome.resolve("config/machine.yaml"), """
                tracker:
                  kind: jira
                  baseUrl: %s
                  credentials: { keysFile: %s, section: TRACKER }
                  workflow: %s
                  mergedStatus: Merged
                  issueType: Story
                """.formatted(jira.address(), keys, workflow));
        return new JiraTracker(new MachineProfile(new EdiHome(profileHome)), calls, new JsonMapper(), cache);
    }

    private List<Exchange> transitionsTaken() {
        return jira.exchanges().stream().filter(exchange -> exchange.method().equals("POST") && exchange.path().endsWith("/transitions")).toList();
    }
}
