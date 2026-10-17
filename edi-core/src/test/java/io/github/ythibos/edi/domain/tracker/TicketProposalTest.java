package io.github.ythibos.edi.domain.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionOption;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.TaskId;

class TicketProposalTest {

    private static final TicketDraft DRAFT = new TicketDraft(new ProjectKey("SVC"), "Story", "Fit the harbour lamp", "Brass, not tin.",
            WorkKey.parse("SVC-9"), List.of("lighting"), "Major");
    private static final Ticket LAMPS = new Ticket(WorkKey.parse("SVC-40"), "Harbour lamps", "In Progress");
    private static final TicketProposal PROPOSAL = new TicketProposal(new TaskId("fit-lamp"), DRAFT, List.of(LAMPS));

    @Test
    void offersEachMatchingTicketAndTheDraftedOneAndRecommendsTheFirstMatch() {
        DecisionCard card = PROPOSAL.card();

        assertThat(card.title()).isEqualTo("Ticket for \"Fit the harbour lamp\"");
        assertThat(card.context()).isEqualTo("""
                fit-lamp books its work on a ticket, and EDI creates a new ticket only on your answer.
                These open SVC tickets match its words.
                Answer with an option, or with the key of another ticket to book it on.""");
        assertThat(card.options()).extracting(DecisionOption::text).containsExactly(
                "Book it on SVC-40: Harbour lamps (In Progress)",
                "Create a Story \"Fit the harbour lamp\" in epic SVC-9, labelled lighting, priority Major in SVC");
        assertThat(card.recommendation().key()).isEqualTo(new OptionKey('A'));
    }

    @Test
    void recommendsCreatingATicketWhenNoneMatches() {
        DecisionCard card = new TicketProposal(new TaskId("fit-lamp"), DRAFT, List.of()).card();

        assertThat(card.options()).hasSize(1);
        assertThat(card.recommendation().why()).isEqualTo("no open ticket matches the work");
    }

    @Test
    void choosesByOptionOrByTheOneKeyTheAnswerNames() {
        assertThat(PROPOSAL.choiceFor(new OptionKey('A'), "D58 A")).isEqualTo(new TicketChoice.BookOn(LAMPS.key()));
        assertThat(PROPOSAL.choiceFor(new OptionKey('B'), "D58 B")).isEqualTo(new TicketChoice.Create(DRAFT));
        assertThat(PROPOSAL.choiceFor(null, "D58: put it on SVC-12 instead")).isEqualTo(new TicketChoice.BookOn(WorkKey.parse("SVC-12")));
        assertThatThrownBy(() -> PROPOSAL.choiceFor(new OptionKey('C'), "D58 C")).isInstanceOf(Refusal.class)
                .hasMessage("The ticket decision for fit-lamp offers no option C.");
        assertThatThrownBy(() -> PROPOSAL.choiceFor(null, "D58: SVC-12 or SVC-13")).isInstanceOf(Refusal.class)
                .hasMessage("The ticket decision for fit-lamp is answered with one of its options, or with the key of the one ticket to book the work on.");
    }

    @Test
    void namesWhyAnEarlierAnswerCouldNotBeCarriedOut() {
        assertThat(PROPOSAL.cardAfterRefusal("JIRA found nothing for the ticket SVC-12 (HTTP 404).").context())
                .startsWith("EDI could not carry out the earlier answer: JIRA found nothing for the ticket SVC-12 (HTTP 404).\nfit-lamp books its work");
    }

    @Test
    void refusesADraftWithoutASummaryOrWithALabelThatIsNotOneWord() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TicketDraft(new ProjectKey("SVC"), "", " ", "", null, List.of(), ""))
                .withMessage("A new ticket has a summary.");
        assertThatIllegalArgumentException().isThrownBy(() -> new TicketDraft(new ProjectKey("SVC"), "", "Fit", "", null, List.of("two words"), ""))
                .withMessage("A ticket label is one word without spaces, not \"two words\".");
    }
}
