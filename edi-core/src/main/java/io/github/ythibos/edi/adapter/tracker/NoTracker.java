package io.github.ythibos.edi.adapter.tracker;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.port.Tracker;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.tracker.SprintPlacement;
import io.github.ythibos.edi.domain.tracker.Ticket;
import io.github.ythibos.edi.domain.tracker.TicketContext;
import io.github.ythibos.edi.domain.tracker.TicketDraft;
import io.github.ythibos.edi.domain.tracker.TicketMove;
import io.github.ythibos.edi.domain.tracker.TrackerKind;

@Component
public class NoTracker implements Tracker {

    @Override
    public TrackerKind kind() {
        return TrackerKind.NONE;
    }

    @Override
    public List<Ticket> findExisting(TicketContext context) {
        return List.of();
    }

    @Override
    public WorkKey create(TicketDraft draft) {
        throw new Refusal("The project names no tracker that could key its work, so the task needs its key given with --key.");
    }

    @Override
    @SuppressWarnings("java:S1186") // a project without a tracker has no ticket to fill
    public void fill(WorkKey key, TicketDraft draft) {
    }

    @Override
    public Ticket verified(WorkKey key) {
        return Ticket.keyedOnly(key);
    }

    @Override
    public Optional<String> browseAddressOf(WorkKey key) {
        return Optional.empty();
    }

    @Override
    public Optional<Ticket> found(WorkKey key) {
        return Optional.of(verified(key));
    }

    @Override
    public TicketMove transition(WorkKey key, String target) {
        return new TicketMove.Untracked();
    }

    @Override
    public SprintPlacement addToCurrentSprint(WorkKey key) {
        return new SprintPlacement.Untracked();
    }

    @Override
    @SuppressWarnings("java:S1186") // a project without a tracker has no ticket to link the draft to
    public void link(WorkKey key, String title, String url) {
    }
}
