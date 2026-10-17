package io.github.ythibos.edi.domain.tracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionOption;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.Recommendation;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.TaskId;

public record TicketProposal(TaskId task, TicketDraft draft, List<Ticket> candidates) {

    public static final int MOST_CANDIDATES = 3;

    public TicketProposal {
        Objects.requireNonNull(task, "A ticket proposal is for a task.");
        Objects.requireNonNull(draft, () -> "The ticket proposal for " + task + " drafts the ticket it would create.");
        candidates = List.copyOf(candidates);
        if (candidates.size() > MOST_CANDIDATES) {
            throw new IllegalArgumentException("The ticket proposal for " + task + " offers at most " + MOST_CANDIDATES + " existing tickets.");
        }
    }

    public DecisionCard card() {
        return new DecisionCard(title(), context(), options(), recommendation());
    }

    public DecisionCard cardAfterRefusal(String because) {
        return new DecisionCard(title(), "EDI could not carry out the earlier answer: " + because + "\n" + context(), options(), recommendation());
    }

    public TicketChoice choiceFor(CaptainAnswer answer) {
        return choiceFor(answer.option(), answer.verbatim());
    }

    public TicketChoice choiceFor(OptionKey option, String verbatim) {
        return option == null ? new TicketChoice.BookOn(theTicketNamedIn(verbatim)) : choiceFor(option);
    }

    private String title() {
        return "Ticket for \"" + draft.summary() + "\"";
    }

    private String context() {
        String found = candidates.isEmpty()
                ? "No open " + draft.project() + " ticket matches its words."
                : "These open " + draft.project() + " tickets match its words.";
        return task + " books its work on a ticket, and EDI creates a new ticket only on your answer.\n" + found
                + "\nAnswer with an option, or with the key of another ticket to book it on.";
    }

    private List<DecisionOption> options() {
        List<DecisionOption> options = new ArrayList<>();
        for (int position = 0; position < candidates.size(); position++) {
            options.add(new DecisionOption(optionAt(position), bookingOn(candidates.get(position))));
        }
        options.add(new DecisionOption(optionAt(candidates.size()), "Create " + draft.described() + " in " + draft.project()));
        return options;
    }

    private static String bookingOn(Ticket candidate) {
        return "Book it on " + candidate.key() + ": " + candidate.summary() + (candidate.status().isEmpty() ? "" : " (" + candidate.status() + ")");
    }

    private Recommendation recommendation() {
        return candidates.isEmpty()
                ? new Recommendation(optionAt(0), "no open ticket matches the work")
                : new Recommendation(optionAt(0), "it is open and matches the work, and a few meaningful tickets serve better than one per item");
    }

    private static OptionKey optionAt(int position) {
        return new OptionKey((char) ('A' + position));
    }

    private WorkKey theTicketNamedIn(String verbatim) {
        List<WorkKey> named = WorkKey.allNamedIn(verbatim);
        if (named.size() != 1) {
            throw new Refusal("The ticket decision for " + task + " is answered with one of its options, or with the key of the one ticket"
                    + " to book the work on.");
        }
        return named.getFirst();
    }

    private TicketChoice choiceFor(OptionKey option) {
        int position = option.letter() - 'A';
        if (position < candidates.size()) {
            return new TicketChoice.BookOn(candidates.get(position).key());
        }
        if (position == candidates.size()) {
            return new TicketChoice.Create(draft);
        }
        throw new Refusal("The ticket decision for " + task + " offers no option " + option + ".");
    }
}
