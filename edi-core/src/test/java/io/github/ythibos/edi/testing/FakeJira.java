package io.github.ythibos.edi.testing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import io.github.ythibos.edi.application.port.Tracker;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.tracker.SprintPlacement;
import io.github.ythibos.edi.domain.tracker.Ticket;
import io.github.ythibos.edi.domain.tracker.TicketContext;
import io.github.ythibos.edi.domain.tracker.TicketDraft;
import io.github.ythibos.edi.domain.tracker.TicketMove;
import io.github.ythibos.edi.domain.tracker.TrackerKind;

public final class FakeJira implements Tracker {

    public static final List<String> WORKFLOW = List.of("To Do", "In Progress", "In Review", "Testing", "Done");

    private final Map<WorkKey, FakeTicket> tickets = new LinkedHashMap<>();
    private final List<TicketDraft> created = new ArrayList<>();
    private final Map<WorkKey, TicketDraft> filled = new LinkedHashMap<>();
    private String activeSprint = "Sprint 7";
    private RuntimeException nextFailure;
    private Refusal fieldsRefusal;
    private int lastNumber = 100;

    @Override
    public TrackerKind kind() {
        return TrackerKind.JIRA;
    }

    @Override
    public synchronized List<Ticket> findExisting(TicketContext context) {
        failIfAsked();
        List<String> words = List.of(context.text().toLowerCase(Locale.ROOT).split("\\W+"));
        return tickets.values().stream()
                .filter(ticket -> ticket.key().project().equals(context.project()) && !"Done".equals(ticket.status()))
                .filter(ticket -> words.stream().anyMatch(word -> word.length() > 2 && ticket.summary().toLowerCase(Locale.ROOT).contains(word)))
                .limit(3)
                .map(FakeTicket::asTicket)
                .toList();
    }

    @Override
    public synchronized WorkKey create(TicketDraft draft) {
        failIfAsked();
        WorkKey key = new WorkKey(draft.project(), ++lastNumber);
        tickets.put(key, new FakeTicket(key, draft.summary(), WORKFLOW.getFirst(), null, new ArrayList<>()));
        created.add(draft);
        return key;
    }

    @Override
    public synchronized void fill(WorkKey key, TicketDraft draft) {
        failIfAsked();
        if (fieldsRefusal != null) {
            throw fieldsRefusal;
        }
        filled.put(key, draft);
    }

    @Override
    public synchronized Ticket verified(WorkKey key) {
        failIfAsked();
        return ticket(key).asTicket();
    }

    @Override
    public synchronized TicketMove transition(WorkKey key, String target) {
        failIfAsked();
        FakeTicket ticket = ticket(key);
        int from = WORKFLOW.indexOf(ticket.status());
        int to = WORKFLOW.indexOf(target);
        if (from == to) {
            return new TicketMove.AlreadyThere(ticket.status());
        }
        if (from > to) {
            return new TicketMove.AlreadyBeyond(ticket.status());
        }
        tickets.put(key, ticket.inStatus(target));
        return new TicketMove.Moved(ticket.status(), WORKFLOW.subList(from + 1, to + 1));
    }

    @Override
    public synchronized SprintPlacement addToCurrentSprint(WorkKey key) {
        failIfAsked();
        FakeTicket ticket = ticket(key);
        if (ticket.sprint() != null) {
            return new SprintPlacement.AlreadyInASprint(ticket.sprint());
        }
        if (activeSprint == null) {
            return new SprintPlacement.NoActiveSprint();
        }
        tickets.put(key, ticket.inSprint(activeSprint));
        return new SprintPlacement.Added(activeSprint);
    }

    @Override
    public synchronized void link(WorkKey key, String title, String url) {
        failIfAsked();
        ticket(key).links().add(url);
    }

    public synchronized FakeJira holding(String key, String summary, String status) {
        WorkKey workKey = WorkKey.parse(key);
        tickets.put(workKey, new FakeTicket(workKey, summary, status, null, new ArrayList<>()));
        return this;
    }

    public synchronized void endsTheSprint() {
        activeSprint = null;
    }

    public synchronized void failsNextWith(RuntimeException failure) {
        nextFailure = failure;
    }

    public synchronized void refusesTheFieldsWith(Refusal refusal) {
        fieldsRefusal = refusal;
    }

    public synchronized List<TicketDraft> created() {
        return List.copyOf(created);
    }

    public synchronized Optional<TicketDraft> fieldsOf(WorkKey key) {
        return Optional.ofNullable(filled.get(key));
    }

    public synchronized String statusOf(String key) {
        return ticket(WorkKey.parse(key)).status();
    }

    public synchronized String sprintOf(String key) {
        return ticket(WorkKey.parse(key)).sprint();
    }

    public synchronized List<String> linksOf(String key) {
        return List.copyOf(ticket(WorkKey.parse(key)).links());
    }

    private void failIfAsked() {
        RuntimeException failure = nextFailure;
        nextFailure = null;
        if (failure != null) {
            throw failure;
        }
    }

    private FakeTicket ticket(WorkKey key) {
        FakeTicket ticket = tickets.get(key);
        if (ticket == null) {
            throw new Refusal("JIRA found nothing for the ticket " + key + " (HTTP 404).");
        }
        return ticket;
    }

    private record FakeTicket(WorkKey key, String summary, String status, String sprint, List<String> links) {

        Ticket asTicket() {
            return new Ticket(key, summary, status);
        }

        FakeTicket inStatus(String newStatus) {
            return new FakeTicket(key, summary, newStatus, sprint, links);
        }

        FakeTicket inSprint(String newSprint) {
            return new FakeTicket(key, summary, status, newSprint, links);
        }
    }
}
