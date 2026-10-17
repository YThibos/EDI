package io.github.ythibos.edi.application.tracker;

import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.TicketProposalStore;
import io.github.ythibos.edi.adapter.tracker.TrackerUnavailable;
import io.github.ythibos.edi.application.decision.RaiseDecision;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.Tracker;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskKeyed;
import io.github.ythibos.edi.domain.event.DomainEvent.TicketChoiceRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.TicketCreated;
import io.github.ythibos.edi.domain.event.DomainEvent.TicketUpdateRefused;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.tracker.ProposedTicket;
import io.github.ythibos.edi.domain.tracker.Ticket;
import io.github.ythibos.edi.domain.tracker.TicketChoice;
import io.github.ythibos.edi.domain.tracker.TicketContext;
import io.github.ythibos.edi.domain.tracker.TicketDraft;
import io.github.ythibos.edi.domain.tracker.TicketProposal;
import io.github.ythibos.edi.domain.tracker.TicketSettlement;

@Service
public class TicketProposals {

    private static final Logger LOG = LoggerFactory.getLogger(TicketProposals.class);
    private static final String THE_FIELDS = "the fields";

    private final TicketProposalStore proposals;
    private final TaskStore tasks;
    private final DecisionStore decisions;
    private final RaiseDecision raiseDecision;
    private final EventLog events;
    private final ProjectRegistry registry;
    private final Trackers trackers;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public TicketProposals(TicketProposalStore proposals, TaskStore tasks, DecisionStore decisions, RaiseDecision raiseDecision, EventLog events,
            ProjectRegistry registry, Trackers trackers, Clock clock, TransactionTemplate transaction) {
        this.proposals = proposals;
        this.tasks = tasks;
        this.decisions = decisions;
        this.raiseDecision = raiseDecision;
        this.events = events;
        this.registry = registry;
        this.trackers = trackers;
        this.clock = clock;
        this.transaction = transaction;
    }

    public DecisionNumber propose(TicketProposal proposal, Actor actor) {
        proposals.insert(ProposedTicket.proposed(proposal, clock.now()));
        return raiseDecision.raise(new TaskReference.ById(proposal.task()), proposal.card(), actor);
    }

    public void requireAChoiceIn(TaskId task, OptionKey option, String verbatim) {
        proposals.of(task)
                .filter(proposed -> !proposed.isSettled())
                .map(proposed -> proposed.proposal().choiceFor(option, verbatim))
                .ifPresent(choice -> requireOwned(task, choice));
    }

    public synchronized TicketSettlement settle(TaskId task, Actor actor) {
        Optional<Choice> chosen = transaction.execute(_ -> chosenAndUnsettled(task));
        if (chosen.isEmpty()) {
            return TicketSettlement.none();
        }
        try {
            return carriedOut(chosen.get(), actor);
        } catch (Refusal refused) {
            askedAgain(chosen.get().proposed(), refused.getMessage());
            return new TicketSettlement.AskedAgain(refused.getMessage());
        } catch (TrackerUnavailable unavailable) {
            LOG.warn("The ticket of {} is not settled yet: {}", task, unavailable.getMessage());
            return new TicketSettlement.Pending(unavailable.getMessage());
        }
    }

    public void settleEveryAnswered() {
        transaction.execute(_ -> proposals.answeredAndUnsettled()).forEach(task -> settle(task, Actor.CORE));
    }

    private void requireOwned(TaskId task, TicketChoice choice) {
        if (choice instanceof TicketChoice.BookOn bookOn) {
            trackers.tracked(projectOf(task)).requireOwns(bookOn.key());
        }
    }

    private ProjectEntry projectOf(TaskId task) {
        return registry.entryOf(tasks.get(task).intake().projects().getFirst());
    }

    private Optional<Choice> chosenAndUnsettled(TaskId task) {
        return proposals.of(task)
                .filter(proposed -> !proposed.isSettled())
                .flatMap(proposed -> decisions.ofTask(task)
                        .filter(decision -> !decision.isOpen())
                        .map(decision -> new Choice(proposed, proposed.proposal().choiceFor(decision.answer()))));
    }

    private TicketSettlement carriedOut(Choice chosen, Actor actor) {
        Tracker tracker = transaction.execute(_ -> trackers.serving(projectOf(chosen.proposed().task())));
        return switch (chosen.choice()) {
            case TicketChoice.BookOn bookOn -> bookedOn(chosen.proposed(), tracker.verified(bookOn.key()), actor);
            case TicketChoice.Create create -> created(chosen.proposed(), tracker, create.draft(), actor);
        };
    }

    private TicketSettlement bookedOn(ProposedTicket proposed, Ticket ticket, Actor actor) {
        transaction.executeWithoutResult(_ -> {
            Instant now = clock.now();
            keyed(proposed.task(), ticket.key(), actor, now);
            proposals.update(proposed.settledOn(ticket.key(), now));
        });
        return new TicketSettlement.Keyed(ticket.key(), false);
    }

    private TicketSettlement created(ProposedTicket proposed, Tracker tracker, TicketDraft draft, Actor actor) {
        ProposedTicket withTicket = proposed.hasCreated() ? proposed : withTheCreatedTicket(proposed, tracker, draft, actor);
        Optional<String> fieldsRefused = withTicket.isFilled() ? Optional.empty() : refusalToFill(tracker, withTicket.createdKey(), draft);
        transaction.executeWithoutResult(_ -> {
            Instant now = clock.now();
            ProposedTicket filled = withTicket.isFilled() || fieldsRefused.isPresent() ? withTicket : withTicket.filled(now);
            proposals.update(filled.settledOn(withTicket.createdKey(), now));
            fieldsRefused.ifPresent(because -> events.record(new TicketUpdateRefused(proposed.task(), withTicket.createdKey(), THE_FIELDS, because),
                    actor, now));
        });
        return fieldsRefused.<TicketSettlement>map(because -> new TicketSettlement.CreatedWithoutItsFields(withTicket.createdKey(), because))
                .orElseGet(() -> new TicketSettlement.Keyed(withTicket.createdKey(), true));
    }

    private ProposedTicket withTheCreatedTicket(ProposedTicket proposed, Tracker tracker, TicketDraft draft, Actor actor) {
        WorkKey key = proposed.triedToCreate() ? adoptedOrCreated(tracker, draft) : createdAfterRecordingTheTry(proposed, tracker, draft);
        return transaction.execute(_ -> {
            Instant now = clock.now();
            ProposedTicket created = proposals.of(proposed.task()).orElseThrow().created(key);
            proposals.update(created);
            events.record(new TicketCreated(proposed.task(), key), actor, now);
            keyed(proposed.task(), key, actor, now);
            return created;
        });
    }

    private static WorkKey adoptedOrCreated(Tracker tracker, TicketDraft draft) {
        return tracker.findExisting(new TicketContext(draft.project(), draft.summary())).stream()
                .filter(ticket -> ticket.summary().equals(draft.summary()))
                .findFirst()
                .map(Ticket::key)
                .orElseGet(() -> tracker.create(draft));
    }

    private WorkKey createdAfterRecordingTheTry(ProposedTicket proposed, Tracker tracker, TicketDraft draft) {
        transaction.executeWithoutResult(_ -> proposals.update(proposed.createTried(clock.now())));
        return tracker.create(draft);
    }

    private void keyed(TaskId task, WorkKey key, Actor actor, Instant now) {
        tasks.update(tasks.get(task).keyedOn(key));
        events.record(new TaskKeyed(task, key), actor, now);
    }

    private static Optional<String> refusalToFill(Tracker tracker, WorkKey key, TicketDraft draft) {
        try {
            tracker.fill(key, draft);
            return Optional.empty();
        } catch (Refusal refused) {
            return Optional.of(refused.getMessage());
        }
    }

    private void askedAgain(ProposedTicket proposed, String because) {
        transaction.executeWithoutResult(_ -> {
            proposals.update(proposals.of(proposed.task()).orElseThrow().refusedBecause(because));
            events.record(new TicketChoiceRefused(proposed.task(), because), Actor.CORE, clock.now());
            raiseDecision.raise(new TaskReference.ById(proposed.task()), proposed.proposal().cardAfterRefusal(because), Actor.CORE);
        });
    }

    private record Choice(ProposedTicket proposed, TicketChoice choice) {
    }
}
