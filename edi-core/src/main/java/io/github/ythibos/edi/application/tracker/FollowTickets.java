package io.github.ythibos.edi.application.tracker;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.LoggedEvent;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.Tracker;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftMerged;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftOpened;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftRecorded;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDispatched;
import io.github.ythibos.edi.domain.event.DomainEvent.TicketAddedToSprint;
import io.github.ythibos.edi.domain.event.DomainEvent.TicketLinked;
import io.github.ythibos.edi.domain.event.DomainEvent.TicketTransitioned;
import io.github.ythibos.edi.domain.event.DomainEvent.TicketUpdateRefused;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.tracker.SprintPlacement;
import io.github.ythibos.edi.domain.tracker.TicketMove;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Component
public class FollowTickets {

    public static final String FOLLOWER = "tickets";

    private static final Logger LOG = LoggerFactory.getLogger(FollowTickets.class);

    private final TaskStore tasks;
    private final ProjectRegistry registry;
    private final Trackers trackers;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public FollowTickets(TaskStore tasks, ProjectRegistry registry, Trackers trackers, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.registry = registry;
        this.trackers = trackers;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public void follow(LoggedEvent event) {
        updateAfter(event).ifPresent(update -> followedTicketOf(event.task()).ifPresent(ticket -> updated(ticket, update, event)));
    }

    private static Optional<TicketUpdate> updateAfter(LoggedEvent event) {
        if (event.isA(TaskDispatched.class) && WorkerId.parse(event.text("worker")).incarnation() == 1) {
            return Optional.of(TicketUpdate.SPRINT_PLACEMENT);
        }
        if (event.isA(DraftRecorded.class) || event.isA(DraftOpened.class)) {
            return Optional.of(TicketUpdate.LINK);
        }
        return event.isA(DraftMerged.class) ? Optional.of(TicketUpdate.TRANSITION) : Optional.empty();
    }

    private Optional<FollowedTicket> followedTicketOf(TaskId id) {
        try {
            return transaction.execute(_ -> ticketOf(tasks.get(id)));
        } catch (Refusal unfollowable) {
            LOG.warn("Not following the ticket of {}: {}", id, unfollowable.getMessage());
            return Optional.empty();
        }
    }

    private Optional<FollowedTicket> ticketOf(Task task) {
        WorkKey key = task.intake().workKey();
        if (key == null || task.intake().projects().isEmpty()) {
            return Optional.empty();
        }
        ProjectEntry project = registry.entryOf(task.intake().projects().getFirst());
        boolean followed = project.tracker().isExternal() && !trackers.tracked(project).isEvergreen(key);
        return followed ? Optional.of(new FollowedTicket(task, key, project)) : Optional.empty();
    }

    private void updated(FollowedTicket ticket, TicketUpdate update, LoggedEvent event) {
        try {
            Tracker tracker = trackers.serving(ticket.project());
            switch (update) {
                case SPRINT_PLACEMENT -> placedInTheSprint(tracker, ticket);
                case LINK -> linked(tracker, ticket, event.text("url"));
                case TRANSITION -> transitioned(tracker, ticket);
            }
        } catch (Refusal refused) {
            recorded(new TicketUpdateRefused(ticket.task().id(), ticket.key(), update.sentence(), refused.getMessage()));
        }
    }

    private void placedInTheSprint(Tracker tracker, FollowedTicket ticket) {
        if (tracker.addToCurrentSprint(ticket.key()) instanceof SprintPlacement.Added added) {
            recorded(new TicketAddedToSprint(ticket.task().id(), ticket.key(), added.sprint()));
        }
    }

    private void linked(Tracker tracker, FollowedTicket ticket, String url) {
        tracker.link(ticket.key(), ticket.task().intake().title(), url);
        recorded(new TicketLinked(ticket.task().id(), ticket.key(), url));
    }

    private void transitioned(Tracker tracker, FollowedTicket ticket) {
        if (tracker.transition(ticket.key(), trackers.mergedStatus()) instanceof TicketMove.Moved moved) {
            recorded(new TicketTransitioned(ticket.task().id(), ticket.key(), moved.from(), moved.to()));
        }
    }

    private void recorded(DomainEvent event) {
        transaction.executeWithoutResult(_ -> events.record(event, Actor.CORE, clock.now()));
    }

    private enum TicketUpdate {
        SPRINT_PLACEMENT("the sprint placement"),
        LINK("the link to its draft"),
        TRANSITION("the transition after its merge");

        private final String sentence;

        TicketUpdate(String sentence) {
            this.sentence = sentence;
        }

        String sentence() {
            return sentence;
        }
    }

    private record FollowedTicket(Task task, WorkKey key, ProjectEntry project) {
    }
}
