package io.github.ythibos.edi.adapter.tracker;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.ythibos.edi.application.port.Tracker;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.publish.EdiVocabulary;
import io.github.ythibos.edi.domain.publish.TextFinding;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.tracker.SprintPlacement;
import io.github.ythibos.edi.domain.tracker.Ticket;
import io.github.ythibos.edi.domain.tracker.TicketContext;
import io.github.ythibos.edi.domain.tracker.TicketDraft;
import io.github.ythibos.edi.domain.tracker.TicketMove;
import io.github.ythibos.edi.domain.tracker.TrackerKind;

public final class PlainlyWordedTracker implements Tracker {

    private static final String SUMMARY = "the summary";
    private static final String DESCRIPTION = "description";
    private static final String LINK_TITLE = "the link's title";

    private final Tracker tracker;

    public PlainlyWordedTracker(Tracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public TrackerKind kind() {
        return tracker.kind();
    }

    @Override
    public List<Ticket> findExisting(TicketContext context) {
        return tracker.findExisting(context);
    }

    @Override
    public WorkKey create(TicketDraft draft) {
        requirePlainWording(draft);
        return tracker.create(draft);
    }

    @Override
    public void fill(WorkKey key, TicketDraft draft) {
        requirePlainWording(draft);
        tracker.fill(key, draft);
    }

    @Override
    public Ticket verified(WorkKey key) {
        return tracker.verified(key);
    }

    @Override
    public Optional<Ticket> found(WorkKey key) {
        return tracker.found(key);
    }

    @Override
    public Optional<String> browseAddressOf(WorkKey key) {
        return tracker.browseAddressOf(key);
    }

    @Override
    public TicketMove transition(WorkKey key, String target) {
        return tracker.transition(key, target);
    }

    @Override
    public SprintPlacement addToCurrentSprint(WorkKey key) {
        return tracker.addToCurrentSprint(key);
    }

    @Override
    public void link(WorkKey key, String title, String url) {
        requirePlainWording(EdiVocabulary.findIn(LINK_TITLE, title));
        tracker.link(key, title, url);
    }

    private static void requirePlainWording(TicketDraft draft) {
        requirePlainWording(Stream.concat(
                        EdiVocabulary.findIn(SUMMARY, draft.summary()).stream(),
                        EdiVocabulary.findInLines(DESCRIPTION, draft.description()).stream())
                .toList());
    }

    private static void requirePlainWording(List<TextFinding> findings) {
        if (!findings.isEmpty()) {
            throw new Refusal(Rule.TRACKER_TEXT_CLEAN, "Text bound for the project's tracker carries internal vocabulary, which the project's readers never see: "
                    + findings.stream().map(TextFinding::toString).collect(Collectors.joining("; ")) + ".");
        }
    }
}
