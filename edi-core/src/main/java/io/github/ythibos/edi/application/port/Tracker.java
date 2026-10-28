package io.github.ythibos.edi.application.port;

import java.util.List;
import java.util.Optional;

import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.tracker.SprintPlacement;
import io.github.ythibos.edi.domain.tracker.Ticket;
import io.github.ythibos.edi.domain.tracker.TicketContext;
import io.github.ythibos.edi.domain.tracker.TicketDraft;
import io.github.ythibos.edi.domain.tracker.TicketMove;
import io.github.ythibos.edi.domain.tracker.TrackerKind;

public interface Tracker {

    TrackerKind kind();

    List<Ticket> findExisting(TicketContext context);

    WorkKey create(TicketDraft draft);

    void fill(WorkKey key, TicketDraft draft);

    Ticket verified(WorkKey key);

    Optional<Ticket> found(WorkKey key);

    TicketMove transition(WorkKey key, String target);

    SprintPlacement addToCurrentSprint(WorkKey key);

    void link(WorkKey key, String title, String url);
}
