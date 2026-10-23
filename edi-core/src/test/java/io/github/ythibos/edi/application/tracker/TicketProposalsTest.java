package io.github.ythibos.edi.application.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.tracker.TrackerUnavailable;
import io.github.ythibos.edi.application.decision.AnsweredDecision;
import io.github.ythibos.edi.application.decision.GivenAnswer;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.tracker.TicketDetails;
import io.github.ythibos.edi.domain.tracker.TicketSettlement;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TrackerFixtures;

class TicketProposalsTest {

    private static final TaskId LAMPS = new TaskId("fix-lamps");

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void proposeATicket() throws IOException {
        home = TrackerFixtures.laptopHome(directory);
        TicketDetails details = new TicketDetails(null, WorkKey.parse("SVC-9"), List.of("lighting"), "Major");
        home.addTask().add(TrackerFixtures.ship("fix-lamps", "service", "Fix the harbour lamps", null, details), Actor.PRIMARY);
    }

    @Test
    void booksTheWorkOnTheTicketTheCaptainChose() {
        AnsweredDecision answered = answered("A", "D58 A");

        assertThat(answered.ticket()).isEqualTo(new TicketSettlement.Keyed(WorkKey.parse("SVC-40"), false));
        assertThat(home.tasks().get(LAMPS).intake().workKey()).isEqualTo(WorkKey.parse("SVC-40"));
        assertThat(home.tasks().get(LAMPS).isHeld()).isFalse();
        assertThat(home.eventTypesOf(LAMPS)).endsWith("DecisionAnswered", "TaskKeyed");
        assertThat(home.jira().created()).isEmpty();
    }

    @Test
    void createsTheDraftedTicketOnlyOnTheCaptainsAnswerAndFillsItsFields() {
        AnsweredDecision answered = answered("B", "D58 B, create it");

        WorkKey created = WorkKey.parse("SVC-101");
        assertThat(answered.ticket()).isEqualTo(new TicketSettlement.Keyed(created, true));
        assertThat(home.jira().created()).singleElement().satisfies(draft -> assertThat(draft.summary()).isEqualTo("Fix the harbour lamps"));
        assertThat(home.jira().fieldsOf(created)).hasValueSatisfying(fields -> {
            assertThat(fields.epic()).isEqualTo(WorkKey.parse("SVC-9"));
            assertThat(fields.labels()).containsExactly("lighting");
            assertThat(fields.priority()).isEqualTo("Major");
            assertThat(fields.description()).isEqualTo("Do fix the harbour lamps.");
        });
        assertThat(home.tasks().get(LAMPS).intake().workKey()).isEqualTo(created);
        assertThat(home.eventTypesOf(LAMPS)).endsWith("DecisionAnswered", "TicketCreated", "TaskKeyed");
        assertThat(home.ticketProposalStore().of(LAMPS).orElseThrow().isSettled()).isTrue();
    }

    @Test
    void refusesAnAnswerWithoutAnOptionTheCardOffers() {
        assertThatThrownBy(() -> answered(null, "D58: whichever you like")).isInstanceOf(Refusal.class)
                .hasMessage("The ticket decision for fix-lamps is answered with one of its options.");
        assertThatThrownBy(() -> answered(null, "D58: book it on SVC-55")).isInstanceOf(Refusal.class)
                .hasMessage("The ticket decision for fix-lamps is answered with one of its options.");
        assertThatThrownBy(() -> answered("D", "D58 D")).isInstanceOf(Refusal.class).hasMessage("The ticket decision for fix-lamps offers no option D.");
        assertThat(home.decisions().ofTask(LAMPS).orElseThrow().isOpen()).isTrue();
    }

    @Test
    void raisesTheDecisionAgainWhenTheTrackerRefusesTheAnswer() {
        home.jira().failsNextWith(new Refusal("JIRA found nothing for the ticket SVC-40 (HTTP 404)."));

        AnsweredDecision answered = answered("A", "D58 A");

        assertThat(answered.ticket()).isEqualTo(new TicketSettlement.AskedAgain("JIRA found nothing for the ticket SVC-40 (HTTP 404)."));
        Decision raisedAgain = home.decisions().ofTask(LAMPS).orElseThrow();
        assertThat(raisedAgain.isOpen()).isTrue();
        assertThat(raisedAgain.number()).isEqualTo(new DecisionNumber(answered.decision().number().value() + 1));
        assertThat(home.decisions().get(answered.decision().number()).answer().verbatim()).isEqualTo("D58 A");
        assertThat(raisedAgain.card().context()).startsWith("EDI could not carry out the earlier answer: JIRA found nothing for the ticket SVC-40");
        assertThat(home.tasks().get(LAMPS).isHeld()).isTrue();
        assertThat(home.eventTypesOf(LAMPS)).endsWith("DecisionAnswered", "TicketChoiceRefused", "DecisionRaised");
        assertThat(home.wakes().unacknowledged()).extracting(queued -> queued.wake().kind()).containsExactly(WakeKind.DECISION_RAISED);
    }

    @Test
    void settlesTheTicketLaterWhenTheTrackerCannotBeReached() {
        home.jira().failsNextWith(new TrackerUnavailable("JIRA at tracker.example.org cannot be reached for the new ticket: ConnectException."));

        AnsweredDecision answered = answered("B", "D58 B");

        assertThat(answered.ticket()).isInstanceOf(TicketSettlement.Pending.class);
        assertThat(home.tasks().get(LAMPS).intake().workKey()).isNull();
        assertThat(home.jira().created()).isEmpty();

        home.ticketProposals().settleEveryAnswered();

        assertThat(home.tasks().get(LAMPS).intake().workKey()).isEqualTo(WorkKey.parse("SVC-101"));
        assertThat(home.wakes().unacknowledged()).extracting(queued -> queued.wake().kind()).containsExactly(WakeKind.TASK_KEYED);
        home.ticketProposals().settleEveryAnswered();
        assertThat(home.jira().created()).hasSize(1);
    }

    @Test
    void adoptsTheTicketAnInterruptedCreationAlreadyMadeInsteadOfCreatingASecond() {
        home.ticketProposalStore().update(home.ticketProposalStore().of(LAMPS).orElseThrow().createTried(TestHome.START));
        home.jira().holding("SVC-90", "Fix the harbour lamps", "To Do");

        assertThat(answered("B", "D58 B").ticket()).isEqualTo(new TicketSettlement.Keyed(WorkKey.parse("SVC-90"), true));
        assertThat(home.jira().created()).isEmpty();
    }

    @Test
    void keepsTheCreatedTicketWhenTheTrackerRefusesItsFields() {
        home.jira().refusesTheFieldsWith(new Refusal("the epic link is not allowed"));

        AnsweredDecision answered = answered("B", "D58 B");

        assertThat(answered.ticket()).isEqualTo(new TicketSettlement.CreatedWithoutItsFields(WorkKey.parse("SVC-101"), "the epic link is not allowed"));
        assertThat(home.tasks().get(LAMPS).intake().workKey()).isEqualTo(WorkKey.parse("SVC-101"));
        assertThat(home.eventTypesOf(LAMPS)).endsWith("TicketCreated", "TaskKeyed", "TicketUpdateRefused");
    }

    private AnsweredDecision answered(String option, String verbatim) {
        return home.answerDecision().answer(new TaskReference.ById(LAMPS),
                new GivenAnswer(verbatim, option == null ? null : OptionKey.parse(option), "", AnswerChannel.CHAT), Actor.PRIMARY);
    }
}
