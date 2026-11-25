package io.github.ythibos.edi.application.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.tracker.TrackerUnavailable;
import io.github.ythibos.edi.application.decision.AnsweredDecision;
import io.github.ythibos.edi.application.decision.GivenAnswer;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.authority.Citation;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.primary.SessionSource;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.domain.tracker.TicketDetails;
import io.github.ythibos.edi.domain.tracker.TicketSettlement;
import io.github.ythibos.edi.testing.Cards;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TrackerFixtures;

class ClosingATicketDecisionTest {

    private static final TaskId LAMPS = new TaskId("fix-lamps");
    private static final TaskReference THE_LAMPS = new TaskReference.ById(LAMPS);

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void proposeATicket() throws IOException {
        home = TrackerFixtures.laptopHome(directory);
        home.addTask().add(TrackerFixtures.ship("fix-lamps", "service", "Fix the harbour lamps", null, TicketDetails.NONE), Actor.PRIMARY);
    }

    @Test
    void theDropOptionBooksNoTicketAndAuthorisesTheDrop() {
        CallingSession session = session("C, drop it");

        AnsweredDecision answered = home.answerDecision().answer(THE_LAMPS, new GivenAnswer("C, drop it", new OptionKey('C'), "", AnswerChannel.CHAT),
                Actor.PRIMARY);

        assertThat(answered.ticket()).isEqualTo(new TicketSettlement.DropChosen());
        assertThat(home.ticketProposalStore().of(LAMPS).orElseThrow().isSettled()).isTrue();
        assertThat(home.dropTask().drop(THE_LAMPS, false, session, Actor.PRIMARY).task().state()).isEqualTo(TaskState.DROPPED);
        assertThat(home.jira().created()).isEmpty();
    }

    @Test
    void theCaptainsDropWordDropsATaskWhoseTicketDecisionIsOpenAndClosesTheDecision() {
        CallingSession session = session("Drop the lamps.");
        home.captainsWord().record(session, new Citation.Verbatim("Drop the lamps."), THE_LAMPS, null, Actor.PRIMARY);

        Task dropped = home.dropTask().drop(THE_LAMPS, false, session, Actor.PRIMARY).task();

        assertThat(dropped.state()).isEqualTo(TaskState.DROPPED);
        Decision closed = home.decisions().ofTask(LAMPS).orElseThrow();
        assertThat(closed.isOpen()).isFalse();
        assertThat(closed.answer().verbatim()).isEqualTo("Drop the lamps.");
        assertThat(home.decisions().open()).isEmpty();
        assertThat(home.ticketProposalStore().of(LAMPS).orElseThrow().isSettled()).isTrue();
        assertThat(home.eventTypesOf(LAMPS)).endsWith("DecisionAnswered", "StageChanged", "TaskDropped");
    }

    @Test
    void aDropSettlesATicketStillPendingSoNoTicketIsCreatedForTheDroppedTask() {
        home.jira().failsNextWith(new TrackerUnavailable("JIRA at tracker.example.org cannot be reached for the new ticket: ConnectException."));
        CallingSession session = session("B, create it");
        home.answerDecision().answer(THE_LAMPS, new GivenAnswer("B, create it", new OptionKey('B'), "", AnswerChannel.CHAT), Actor.PRIMARY);
        home.primaryHooks().promptSubmitted(session.id(), "Drop the lamps after all.");
        home.captainsWord().record(session, new Citation.Verbatim("Drop the lamps after all."), THE_LAMPS, null, Actor.PRIMARY);

        home.dropTask().drop(THE_LAMPS, false, session, Actor.PRIMARY);
        home.ticketProposals().settleEveryAnswered();

        assertThat(home.jira().created()).isEmpty();
        assertThat(home.tasks().get(LAMPS).intake().workKey()).isNull();
    }

    @Test
    void keyingATaskAfterIntakeBooksItOnTheKeyAndClosesItsTicketDecision() {
        CallingSession session = session("It books on SVC-2.");

        KeyedTask keyed = home.keyTask().key(THE_LAMPS, WorkKey.parse("SVC-2"), new Citation.Verbatim("It books on SVC-2."), session, Actor.PRIMARY);

        assertThat(keyed.task().intake().workKey()).isEqualTo(WorkKey.parse("SVC-2"));
        assertThat(keyed.task().isHeld()).isFalse();
        assertThat(keyed.closedDecision()).isEqualTo(home.decisions().ofTask(LAMPS).orElseThrow().number());
        assertThat(home.decisions().ofTask(LAMPS).orElseThrow().answer().verbatim()).isEqualTo("It books on SVC-2.");
        assertThat(home.ticketProposalStore().of(LAMPS).orElseThrow().settledKey()).isEqualTo(WorkKey.parse("SVC-2"));
        assertThat(home.eventTypesOf(LAMPS)).endsWith("DecisionAnswered", "TaskKeyed");
    }

    @Test
    void keyingATaskWhoseTicketDecisionIsOpenTakesTheCaptainsWords() {
        CallingSession session = session("Hello.");

        assertThatThrownBy(() -> home.keyTask().key(THE_LAMPS, WorkKey.parse("SVC-2"), null, session, Actor.PRIMARY)).isInstanceOf(Refusal.class)
                .hasMessageContaining("so its key answers that decision: cite the captain's words");
        assertThat(home.decisions().ofTask(LAMPS).orElseThrow().isOpen()).isTrue();
        assertThat(home.tasks().get(LAMPS).intake().workKey()).isNull();
    }

    @Test
    void keysATaskOnlyOnATicketItsTrackerHolds() {
        CallingSession session = session("It books on SVC-77.");

        assertThatThrownBy(() -> home.keyTask().key(THE_LAMPS, WorkKey.parse("SVC-77"), new Citation.Verbatim("It books on SVC-77."), session,
                Actor.PRIMARY)).isInstanceOf(Refusal.class);
        assertThatThrownBy(() -> home.keyTask().key(THE_LAMPS, WorkKey.parse("HRN-1"), new Citation.Verbatim("It books on SVC-77."), session,
                Actor.PRIMARY)).isInstanceOf(Refusal.class);
        assertThat(home.decisions().ofTask(LAMPS).orElseThrow().isOpen()).isTrue();
    }

    @Test
    void keysAnUnkeyedTaskThatAwaitsNoDecisionWithoutTheCaptainsWords() {
        home.addTask().add(TrackerFixtures.ship("weed-beds", "garden", "Weed the beds", null), Actor.PRIMARY);
        CallingSession session = session("Hello.");

        KeyedTask keyed = home.keyTask().key(TaskReference.parse("weed-beds"), WorkKey.parse("GDN-3"), null, session, Actor.PRIMARY);

        assertThat(keyed.task().intake().workKey()).isEqualTo(WorkKey.parse("GDN-3"));
        assertThat(keyed.closedDecision()).isNull();
    }

    @Test
    void carriesOutTheTicketChoiceOnlyFromTheAnswerToTheTicketDecision() {
        DecisionNumber ticket = home.decisions().ofTask(LAMPS).orElseThrow().number();
        DecisionNumber bulb = home.raiseDecision().raise(THE_LAMPS, Cards.card("Which bulb?"), Actor.PRIMARY);

        AnsweredDecision bulbAnswered = home.answerDecision().answer(new TaskReference.ByDecision(bulb),
                new GivenAnswer("A", new OptionKey('A'), "", AnswerChannel.CHAT), Actor.PRIMARY);
        AnsweredDecision ticketAnswered = home.answerDecision().answer(new TaskReference.ByDecision(ticket),
                new GivenAnswer("C, drop it", new OptionKey('C'), "", AnswerChannel.CHAT), Actor.PRIMARY);

        assertThat(bulbAnswered.ticket()).isEqualTo(TicketSettlement.none());
        assertThat(bulbAnswered.task().holds().openDecisions()).containsExactly(ticket);
        assertThat(ticketAnswered.ticket()).isEqualTo(new TicketSettlement.DropChosen());
        assertThat(home.tasks().get(LAMPS).intake().workKey()).isNull();
    }

    @Test
    void refusesTheDropWhileAnotherDecisionWaitsBesideTheTicketDecision() {
        CallingSession session = session("Drop the lamps.");
        home.captainsWord().record(session, new Citation.Verbatim("Drop the lamps."), THE_LAMPS, null, Actor.PRIMARY);
        DecisionNumber ticket = home.decisions().ofTask(LAMPS).orElseThrow().number();
        DecisionNumber bulb = home.raiseDecision().raise(THE_LAMPS, Cards.card("Which bulb?"), Actor.PRIMARY);

        assertThatThrownBy(() -> home.dropTask().drop(THE_LAMPS, false, session, Actor.PRIMARY)).isInstanceOf(Refusal.class)
                .hasMessage("fix-lamps waits on " + ticket + " and " + bulb + "; it is dropped once those decisions are answered.");
        assertThat(home.decisions().open()).extracting(Decision::number).containsExactly(ticket, bulb);
        assertThat(home.tasks().get(LAMPS).state()).isEqualTo(TaskState.QUEUED);
    }

    @Test
    void keyingClosesTheTicketDecisionAndLeavesALaterQuestionOpen() {
        CallingSession session = session("It books on SVC-2.");
        DecisionNumber ticket = home.decisions().ofTask(LAMPS).orElseThrow().number();
        DecisionNumber bulb = home.raiseDecision().raise(THE_LAMPS, Cards.card("Which bulb?"), Actor.PRIMARY);

        KeyedTask keyed = home.keyTask().key(THE_LAMPS, WorkKey.parse("SVC-2"), new Citation.Verbatim("It books on SVC-2."), session, Actor.PRIMARY);

        assertThat(keyed.closedDecision()).isEqualTo(ticket);
        assertThat(keyed.task().holds().openDecisions()).containsExactly(bulb);
        assertThat(home.decisions().open()).extracting(Decision::number).containsExactly(bulb);
    }

    private CallingSession session(String typed) {
        UUID session = UUID.randomUUID();
        home.primaryHooks().sessionStarted(session, null, SessionSource.LAUNCH);
        home.primaryHooks().promptSubmitted(session, typed);
        return new CallingSession(session);
    }
}
