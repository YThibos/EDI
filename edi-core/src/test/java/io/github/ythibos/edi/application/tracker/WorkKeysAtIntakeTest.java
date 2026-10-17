package io.github.ythibos.edi.application.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.task.AddedTask;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionOption;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.tracker.Ticket;
import io.github.ythibos.edi.domain.tracker.TicketDetails;
import io.github.ythibos.edi.testing.MachineFixture;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TrackerFixtures;

class WorkKeysAtIntakeTest {

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void openALaptopHome() throws IOException {
        home = TrackerFixtures.laptopHome(directory);
    }

    @Test
    void issuesTheNextEdiNativeKeyToUnkeyedWork() {
        AddedTask first = home.addTask().add(TrackerFixtures.ship("fit-lamp", "harness", "Fit the lamp", null), Actor.PRIMARY);
        home.addTask().add(TrackerFixtures.ship("fit-horn", "harness", "Fit the horn", "HRN-7"), Actor.PRIMARY);
        AddedTask third = home.addTask().add(TrackerFixtures.ship("fit-bell", "harness", "Fit the bell", null), Actor.PRIMARY);

        assertThat(first.ticket()).isEqualTo(Ticket.keyedOnly(WorkKey.parse("HRN-1")));
        assertThat(home.tasks().get(new TaskId("fit-lamp")).intake().workKey()).isEqualTo(WorkKey.parse("HRN-1"));
        assertThat(third.ticket().key()).isEqualTo(WorkKey.parse("HRN-8"));
        assertThat(first.awaitsItsTicket()).isFalse();
    }

    @Test
    void asksTheCaptainWhichTicketUnkeyedTrackerWorkBooksOn() {
        AddedTask added = home.addTask().add(TrackerFixtures.ship("fix-lamps", "service", "Fix the harbour lamps", null), Actor.PRIMARY);

        assertThat(added.awaitsItsTicket()).isTrue();
        Decision decision = home.decisions().ofTask(new TaskId("fix-lamps")).orElseThrow();
        assertThat(decision.number()).isEqualTo(added.ticketDecision());
        assertThat(decision.card().options()).extracting(DecisionOption::text).containsExactly(
                "Book it on SVC-40: Harbour lamps (To Do)",
                "Create a Story \"Fix the harbour lamps\" in SVC");
        assertThat(home.tasks().get(new TaskId("fix-lamps")).isHeld()).isTrue();
        assertThat(home.tasks().get(new TaskId("fix-lamps")).intake().workKey()).isNull();
        assertThat(home.jira().created()).isEmpty();
    }

    @Test
    void takesAGivenKeyOnlyWhenTheProjectsTrackerOwnsItAndItExists() {
        AddedTask lamps = home.addTask().add(TrackerFixtures.ship("fix-lamps", "service", "Fix the lamps", "SVC-40"), Actor.PRIMARY);
        AddedTask coordination = home.addTask().add(TrackerFixtures.ship("plan-week", "service", "Plan the week", "SVC-1"), Actor.PRIMARY);

        assertThat(lamps.ticket()).isEqualTo(new Ticket(WorkKey.parse("SVC-40"), "Harbour lamps", "To Do"));
        assertThat(coordination.ticket().summary()).isEqualTo("Project coordination and support");
        assertThatThrownBy(() -> home.addTask().add(TrackerFixtures.ship("fix-horn", "service", "Fix the horn", "HRN-3"), Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessage("HRN-3 is not a service key: its work is keyed SVC-<n> or on an evergreen ticket.");
        assertThatThrownBy(() -> home.addTask().add(TrackerFixtures.ship("fix-bell", "service", "Fix the bell", "SVC-9"), Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessage("JIRA found nothing for the ticket SVC-9 (HTTP 404).");
        assertThat(home.tasks().contains(new TaskId("fix-bell"))).isFalse();
    }

    @Test
    void queuesUnkeyedWorkOnAProjectWithoutATrackerForItsKeyToBeGivenBeforeDispatch() {
        AddedTask added = home.addTask().add(TrackerFixtures.ship("weed-garden", "garden", "Weed the garden", null), Actor.PRIMARY);

        assertThat(added.ticket()).isNull();
        assertThat(home.tasks().get(new TaskId("weed-garden")).intake().workKey()).isNull();
    }

    @Test
    void takesTicketDetailsOnlyForATicketTheCaptainIsAskedToCreate() {
        TicketDetails details = new TicketDetails("Bug", WorkKey.parse("SVC-9"), List.of("lighting"), "Major");

        home.addTask().add(TrackerFixtures.ship("fix-lamps", "service", "Fix the harbour lamps", null, details), Actor.PRIMARY);

        assertThat(home.decisions().ofTask(new TaskId("fix-lamps")).orElseThrow().card().options()).extracting(DecisionOption::text)
                .contains("Create a Bug \"Fix the harbour lamps\" in epic SVC-9, labelled lighting, priority Major in SVC");
        assertThatThrownBy(() -> home.addTask().add(TrackerFixtures.ship("fit-lamp", "harness", "Fit the lamp", null, details), Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessageStartingWith("Ticket details describe a new ticket of an external tracker");
        assertThatThrownBy(() -> home.addTask().add(TrackerFixtures.ship("fix-horn", "service", "Fix the horn", "SVC-40", details), Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessageStartingWith("Ticket details describe a new ticket of an external tracker");
    }

    @Test
    void refusesTrackerWorkOnAMachineWhoseProfileNamesNoSuchTracker() throws IOException {
        home.on(MachineFixture.DESKTOP);

        assertThatThrownBy(() -> home.addTask().add(TrackerFixtures.ship("fix-lamps", "service", "Fix the lamps", null), Actor.PRIMARY))
                .isInstanceOf(Refusal.class)
                .hasMessage("service books its work on jira, but this machine's profile names no jira tracker; register the project with EDI-native keys"
                        + " on this machine.");
        assertThat(home.addTask().add(TrackerFixtures.ship("fit-lamp", "harness", "Fit the lamp", null), Actor.PRIMARY).ticket().key())
                .isEqualTo(WorkKey.parse("HRN-1"));
    }
}
