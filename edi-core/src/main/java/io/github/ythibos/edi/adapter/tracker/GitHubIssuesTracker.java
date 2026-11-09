package io.github.ythibos.edi.adapter.tracker;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.port.Tracker;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.tracker.SprintPlacement;
import io.github.ythibos.edi.domain.tracker.Ticket;
import io.github.ythibos.edi.domain.tracker.TicketContext;
import io.github.ythibos.edi.domain.tracker.TicketDraft;
import io.github.ythibos.edi.domain.tracker.TicketMove;
import io.github.ythibos.edi.domain.tracker.TrackerKind;

@Component
public class GitHubIssuesTracker implements Tracker {

    @Override
    public TrackerKind kind() {
        return TrackerKind.GITHUB;
    }

    @Override
    public List<Ticket> findExisting(TicketContext context) {
        return List.of();
    }

    @Override
    public WorkKey create(TicketDraft draft) {
        throw new Refusal(Rule.WORK_KEY, draft.project()
                + " keys its work by its GitHub issue numbers, so I never allocate one; open the issue and give its key.");
    }

    @Override
    @SuppressWarnings("java:S1186") // the issue holds the spec, and the task itself carries its title and notes
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
    @SuppressWarnings("java:S1186") // the pull request that closes the issue links the two on the forge
    public void link(WorkKey key, String title, String url) {
    }
}
