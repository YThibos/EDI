package io.github.ythibos.edi.adapter.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.delivery.PipelineReview;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.UnplannedShip;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.tracker.ProposedTicket;
import io.github.ythibos.edi.domain.tracker.Ticket;
import io.github.ythibos.edi.domain.tracker.TicketDraft;
import io.github.ythibos.edi.domain.tracker.TicketProposal;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class TrackerStoresTest {

    private static final TaskId LAMP = new TaskId("fit-lamp");

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void storeTasks() throws IOException {
        home = TestHome.in(directory);
        home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-7"), WorkerFixtures.queuedShip("plan-lamp", "harness", "HRN-7")));
    }

    @Test
    void issuesTheNextKeyOfAPrefixAfterTheHighestOneAnyTaskCarries() {
        WorkKeySequenceStore sequences = new WorkKeySequenceStore(home.jdbc());

        assertThat(sequences.next(new ProjectKey("HRN"))).isEqualTo(WorkKey.parse("HRN-8"));
        assertThat(sequences.next(new ProjectKey("HRN"))).isEqualTo(WorkKey.parse("HRN-9"));
        assertThat(sequences.next(new ProjectKey("HR"))).isEqualTo(WorkKey.parse("HR-1"));
    }

    @Test
    void keepsAProposedTicketWithItsCandidatesAndItsProgress() {
        TicketDraft draft = new TicketDraft(new ProjectKey("SVC"), "Story", "Fit the lamp", "Brass.", WorkKey.parse("SVC-9"), List.of("a", "b"), "Major");
        TicketProposal proposal = new TicketProposal(LAMP, draft, List.of(new Ticket(WorkKey.parse("SVC-40"), "Harbour lamps", "To Do")));
        ProposedTicket proposed = ProposedTicket.proposed(proposal, TestHome.START);
        home.ticketProposalStore().insert(proposed);
        ProposedTicket progressed = proposed.createTried(TestHome.START.plusSeconds(1)).created(WorkKey.parse("SVC-101"))
                .filled(TestHome.START.plusSeconds(2));
        home.ticketProposalStore().update(progressed);

        assertThat(home.ticketProposalStore().of(LAMP)).contains(progressed);
        assertThat(home.ticketProposalStore().answeredAndUnsettled()).isEmpty();
    }

    @Test
    void listsTheAnsweredProposalsThatAreNotSettledYet() {
        TicketProposal proposal = new TicketProposal(LAMP,
                new TicketDraft(new ProjectKey("SVC"), "Story", "Fit the lamp", "", null, List.of(), ""), List.of());
        home.ticketProposalStore().insert(ProposedTicket.proposed(proposal, TestHome.START));
        DecisionCard card = proposal.card();
        home.raiseDecision().raise(new TaskReference.ById(LAMP), card, Actor.PRIMARY);
        assertThat(home.ticketProposalStore().answeredAndUnsettled()).isEmpty();

        Decision raised = home.decisions().ofTask(LAMP).orElseThrow();
        home.decisions().save(raised.answered(new CaptainAnswer("D58 A", new OptionKey('A'), "", TestHome.START, AnswerChannel.CHAT)));

        assertThat(home.ticketProposalStore().answeredAndUnsettled()).containsExactly(LAMP);
    }

    @Test
    void movesAFollowersCursorForward() {
        EventCursorStore cursors = new EventCursorStore(home.jdbc());

        assertThat(cursors.positionOf("tickets")).isZero();
        cursors.moveTo("tickets", 12);
        assertThat(cursors.positionOf("tickets")).isEqualTo(12);
        assertThat(cursors.positionOf("fleet-ledger")).isZero();
    }

    @Test
    void keepsUnplannedShipsAndListsTheFinishedOnes() {
        UnplannedShip piloted = new UnplannedShip(LAMP, CategoryId.IMPLEMENTATION_UNPLANNED, new TaskId("plan-lamp"), TestHome.START);
        home.unplannedShips().insert(piloted);

        assertThat(home.unplannedShips().of(LAMP)).contains(piloted);
        assertThat(home.unplannedShips().finished()).isEmpty();
        home.markDone(LAMP);
        assertThat(home.unplannedShips().finished()).containsExactly(piloted);
    }

    @Test
    void keepsTheReviewOfATaskWithItsUnknownFigures() {
        home.reviews().save(LAMP, new PipelineReview(3, null), TestHome.START);

        assertThat(home.reviews().of(LAMP)).contains(new PipelineReview(3, null));
        home.reviews().save(LAMP, new PipelineReview(2, 5), TestHome.START.plusSeconds(60));
        assertThat(home.reviews().of(LAMP)).contains(new PipelineReview(2, 5));
    }
}
