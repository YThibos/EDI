package io.github.ythibos.edi.adapter.tracker;

import java.util.List;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.store.WorkKeySequenceStore;
import io.github.ythibos.edi.application.port.Tracker;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.tracker.SprintPlacement;
import io.github.ythibos.edi.domain.tracker.Ticket;
import io.github.ythibos.edi.domain.tracker.TicketContext;
import io.github.ythibos.edi.domain.tracker.TicketDraft;
import io.github.ythibos.edi.domain.tracker.TicketMove;
import io.github.ythibos.edi.domain.tracker.TrackerKind;

@Component
public class EdiNativeTracker implements Tracker {

    private final WorkKeySequenceStore sequences;

    public EdiNativeTracker(WorkKeySequenceStore sequences) {
        this.sequences = sequences;
    }

    @Override
    public TrackerKind kind() {
        return TrackerKind.EDI;
    }

    @Override
    public List<Ticket> findExisting(TicketContext context) {
        return List.of();
    }

    @Override
    public WorkKey create(TicketDraft draft) {
        return sequences.next(draft.project());
    }

    @Override
    @SuppressWarnings("java:S1186") // an EDI-native key is the whole ticket, and the task itself carries its title and notes
    public void fill(WorkKey key, TicketDraft draft) {
    }

    @Override
    public Ticket verified(WorkKey key) {
        return Ticket.keyedOnly(key);
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
    @SuppressWarnings("java:S1186") // the task records its draft's address, and an EDI-native key has nowhere else to hold it
    public void link(WorkKey key, String title, String url) {
    }
}
