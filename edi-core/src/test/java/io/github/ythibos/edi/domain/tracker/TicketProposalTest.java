package io.github.ythibos.edi.domain.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CaptainWordAct;
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
                fit-lamp books its work on a ticket, and I create a new ticket only on your answer.
                These open SVC tickets match its words.""");
        assertThat(card.options()).extracting(DecisionOption::text).containsExactly(
                "Book it on SVC-40: Harbour lamps (In Progress)",
                "Create a Story \"Fit the harbour lamp\" in epic SVC-9, labelled lighting, priority Major in SVC",
                "Drop this task, with no ticket");
        assertThat(card.recommendation().key()).isEqualTo(new OptionKey('A'));
    }

    @Test
    void offersToDropTheTaskOnTheCaptainsWordBesideCreatingTheTicket() {
        DecisionCard card = new TicketProposal(new TaskId("fit-lamp"), DRAFT, List.of()).card();

        assertThat(card.options()).extracting(DecisionOption::key).containsExactly(new OptionKey('A'), new OptionKey('B'));
        assertThat(card.authorises(new OptionKey('B'), CaptainWordAct.DROP)).isTrue();
        assertThat(card.authorises(new OptionKey('A'), CaptainWordAct.DROP)).isFalse();
    }

    @Test
    void recommendsCreatingATicketWhenNoneMatches() {
        DecisionCard card = new TicketProposal(new TaskId("fit-lamp"), DRAFT, List.of()).card();

        assertThat(card.options()).extracting(DecisionOption::text).first().asString().startsWith("Create a Story");
        assertThat(card.recommendation().why()).isEqualTo("no open ticket matches the work");
    }

    @Test
    void choosesOnlyByAnOptionTheCardOffers() {
        assertThat(PROPOSAL.choiceFor(new OptionKey('A'))).isEqualTo(new TicketChoice.BookOn(LAMPS.key()));
        assertThat(PROPOSAL.choiceFor(new OptionKey('B'))).isEqualTo(new TicketChoice.Create(DRAFT));
        assertThat(PROPOSAL.choiceFor(new OptionKey('C'))).isEqualTo(new TicketChoice.DropTheTask());
        assertThatThrownBy(() -> PROPOSAL.choiceFor(new OptionKey('D'))).isInstanceOf(Refusal.class)
                .hasMessage("The ticket decision for fit-lamp offers no option D.");
        assertThatThrownBy(() -> PROPOSAL.choiceFor((OptionKey) null)).isInstanceOf(Refusal.class)
                .hasMessage("The ticket decision for fit-lamp is answered with one of its options.");
    }

    @Test
    void namesWhyAnEarlierAnswerCouldNotBeCarriedOut() {
        assertThat(PROPOSAL.cardAfterRefusal("JIRA found nothing for the ticket SVC-12 (HTTP 404).").context())
                .startsWith("I could not carry out the earlier answer: JIRA found nothing for the ticket SVC-12 (HTTP 404).\nfit-lamp books its work");
    }

    @Test
    void refusesADraftWithoutASummaryOrWithALabelThatIsNotOneWord() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TicketDraft(new ProjectKey("SVC"), "", " ", "", null, List.of(), ""))
                .withMessage("A new ticket has a summary.");
        assertThatIllegalArgumentException().isThrownBy(() -> new TicketDraft(new ProjectKey("SVC"), "", "Fit", "", null, List.of("two words"), ""))
                .withMessage("A ticket label is one word without spaces, not \"two words\".");
    }
}
