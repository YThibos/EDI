package io.github.ythibos.edi.adapter.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.tracker.SprintPlacement;
import io.github.ythibos.edi.domain.tracker.Ticket;
import io.github.ythibos.edi.domain.tracker.TicketContext;
import io.github.ythibos.edi.domain.tracker.TicketDraft;
import io.github.ythibos.edi.domain.tracker.TicketMove;
import io.github.ythibos.edi.testing.StubJiraServer;
import io.github.ythibos.edi.testing.StubJiraServer.Exchange;
import tools.jackson.databind.json.JsonMapper;

@Tag("adapter")
class JiraTrackerTest {

    private static final String TOKEN = "jira-token-for-the-stub-only";
    private static final WorkKey LAMPS = WorkKey.parse("SVC-40");

    @TempDir
    private Path home;

    private StubJiraServer jira;
    private JiraCalls calls;
    private JiraTracker tracker;

    @BeforeEach
    void connectToTheStub() throws IOException {
        jira = StubJiraServer.acceptingToken(TOKEN).holding("SVC-40", "Harbour lamps", "To Do").holding("SVC-41", "Harbour bells", "Done");
        Path keys = Files.writeString(home.resolve("keys.yml"), "TRACKER:\n  domain: tracker.example.org\n  API-key: " + TOKEN + "\n");
        Files.createDirectories(home.resolve("config"));
        Files.writeString(home.resolve("config/machine.yaml"), """
                tracker:
                  kind: jira
                  baseUrl: %s
                  credentials: { keysFile: %s, section: TRACKER }
                  workflow: [To Do, In Progress, In Review, Testing, Done]
                  mergedStatus: Testing
                  issueType: Story
                  epicLinkField: customfield_10008
                """.formatted(jira.address(), keys));
        calls = new JiraCalls(new JsonMapper());
        tracker = new JiraTracker(new MachineProfile(new EdiHome(home)), calls, new JsonMapper());
    }

    @AfterEach
    void disconnect() {
        calls.close();
        jira.close();
    }

    @Test
    void findsOpenTicketsByTheWordsOfTheWorkWithTheTokenOfTheKeysFile() {
        List<Ticket> found = tracker.findExisting(new TicketContext(new ProjectKey("SVC"), "Fix the harbour lamps, at last"));

        assertThat(found).containsExactly(new Ticket(LAMPS, "Harbour lamps", "To Do"));
        Exchange search = jira.exchanges().getLast();
        assertThat(search.authorization()).isEqualTo("Bearer " + TOKEN);
        assertThat(search.query()).contains("jql=project = \"SVC\" AND statusCategory != Done AND text ~ \"Fix the harbour lamps last\" ORDER BY updated DESC",
                "fields=summary,status", "maxResults=3");
    }

    @Test
    void createsATicketAndThenFillsEveryFieldWithItsOwnCalls() {
        TicketDraft draft = new TicketDraft(new ProjectKey("SVC"), "", "Fit the harbour lamp", "Brass, not tin.", WorkKey.parse("SVC-9"),
                List.of("lighting", "harbour"), "Major");

        WorkKey created = tracker.create(draft);
        tracker.fill(created, draft);

        assertThat(created).isEqualTo(WorkKey.parse("SVC-101"));
        List<Exchange> writes = jira.exchanges().stream().filter(exchange -> !exchange.method().equals("GET")).toList();
        assertThat(writes).extracting(Exchange::method, Exchange::path).containsExactly(
                tuple("POST", "/rest/api/2/issue"),
                tuple("PUT", "/rest/api/2/issue/SVC-101"),
                tuple("PUT", "/rest/api/2/issue/SVC-101"));
        assertThat(writes.get(0).body()).isEqualTo(
                "{\"fields\":{\"project\":{\"key\":\"SVC\"},\"issuetype\":{\"name\":\"Story\"},\"summary\":\"Fit the harbour lamp\"}}");
        assertThat(writes.get(1).body()).isEqualTo(
                "{\"fields\":{\"description\":\"Brass, not tin.\",\"labels\":[\"lighting\",\"harbour\"],\"customfield_10008\":\"SVC-9\"}}");
        assertThat(writes.get(2).body()).isEqualTo("{\"fields\":{\"priority\":{\"name\":\"Major\"}}}");
    }

    @Test
    void verifiesATicketAndRefusesOneThatDoesNotExist() {
        assertThat(tracker.verified(LAMPS)).isEqualTo(new Ticket(LAMPS, "Harbour lamps", "To Do"));
        assertThatThrownBy(() -> tracker.verified(WorkKey.parse("SVC-9"))).isInstanceOf(Refusal.class)
                .hasMessage("JIRA found nothing for the ticket SVC-9 (HTTP 404).");
    }

    @Test
    void walksTheWorkflowToTheTargetAndLeavesATicketAlreadyThere() {
        TicketMove moved = tracker.transition(LAMPS, "Testing");

        assertThat(moved).isEqualTo(new TicketMove.Moved("To Do", List.of("In Progress", "In Review", "Testing")));
        assertThat(jira.statusOf("SVC-40")).isEqualTo("Testing");
        assertThat(tracker.transition(LAMPS, "Testing")).isEqualTo(new TicketMove.AlreadyThere("Testing"));
        assertThat(tracker.transition(WorkKey.parse("SVC-41"), "Testing")).isEqualTo(new TicketMove.AlreadyBeyond("Done"));
    }

    @Test
    void addsATicketToTheOneActiveSprint() {
        assertThat(tracker.addToCurrentSprint(LAMPS)).isEqualTo(new SprintPlacement.Added("Sprint 7"));
        assertThat(jira.sprintOf("SVC-40")).isEqualTo("Sprint 7");
        assertThat(tracker.addToCurrentSprint(LAMPS)).isEqualTo(new SprintPlacement.AlreadyInASprint("Sprint 7"));
    }

    @Test
    void addsNoTicketWhenNoSprintOrSeveralSprintsAreActive() {
        jira.withActiveSprints(Map.of());
        assertThat(tracker.addToCurrentSprint(LAMPS)).isEqualTo(new SprintPlacement.NoActiveSprint());

        jira.withActiveSprints(Map.of(7L, "Sprint 7", 8L, "Hotfixes"));
        assertThatThrownBy(() -> tracker.addToCurrentSprint(LAMPS)).isInstanceOf(Refusal.class).hasMessageContaining("so EDI adds SVC-40 to none of them");
    }

    @Test
    void linksTheDraftUnderItsAddressSoALinkIsNeverRepeated() {
        tracker.link(LAMPS, "Fit the harbour lamp", "https://forge.example.org/team/service/merge_requests/7");

        Exchange link = jira.exchanges().getLast();
        assertThat(link.path()).isEqualTo("/rest/api/2/issue/SVC-40/remotelink");
        assertThat(link.body()).isEqualTo("{\"globalId\":\"https://forge.example.org/team/service/merge_requests/7\",\"object\":"
                + "{\"url\":\"https://forge.example.org/team/service/merge_requests/7\",\"title\":\"Fit the harbour lamp\"}}");
    }

    @Test
    void namesARefusalWithoutTheTokenAndTellsAnUnreachableTrackerApart() {
        jira.answersNextWith(400, "{\"errorMessages\":[\"Field summary rejected for " + TOKEN + "\"],\"errors\":{\"labels\":\"too long\"}}");
        assertThatThrownBy(() -> tracker.verified(LAMPS)).isInstanceOf(Refusal.class)
                .hasMessage("JIRA refused the ticket SVC-40 (HTTP 400): Field summary rejected for [secret]; labels: too long.");

        jira.answersNextWith(401, "");
        assertThatThrownBy(() -> tracker.verified(LAMPS)).isInstanceOf(Refusal.class)
                .hasMessage("JIRA refused EDI's access token for the ticket SVC-40 (HTTP 401); renew the token in the TRACKER section of the keys file.");

        jira.answersNextWith(503, "{\"errorMessages\":[\"Down for maintenance\"]}");
        assertThatThrownBy(() -> tracker.verified(LAMPS)).isInstanceOf(TrackerUnavailable.class)
                .hasMessage("JIRA could not answer for the ticket SVC-40 (HTTP 503): Down for maintenance.");

        jira.close();
        assertThatThrownBy(() -> tracker.verified(LAMPS)).isInstanceOf(TrackerUnavailable.class)
                .hasMessageStartingWith("JIRA at 127.0.0.1 cannot be reached for the ticket SVC-40: ");
    }

    @Test
    void refusesToCallWhenTheMachineNamesNoJiraTracker() throws IOException {
        Files.writeString(home.resolve("config/machine.yaml"), "tracker:\n  kind: none\n");

        assertThatThrownBy(() -> tracker.verified(LAMPS)).isInstanceOf(Refusal.class)
                .hasMessage("This machine's profile names no JIRA tracker, so EDI cannot reach one.");
    }
}
