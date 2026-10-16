package io.github.ythibos.edi.application.delivery;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.store.DeliveryStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.Forge;
import io.github.ythibos.edi.application.publish.PublishCheck;
import io.github.ythibos.edi.application.worker.WorkerContext;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.DraftProposal;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftOpened;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftRecorded;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.publish.PublishVerdict;
import io.github.ythibos.edi.domain.publish.RevisionRange;

@Service
public class DeliverDraft {

    private static final String ORIGIN = "origin";
    private static final String REPORT_FILE = "report.md";

    private final TaskStore tasks;
    private final DeliveryStore deliveries;
    private final EventLog events;
    private final WorkerContext context;
    private final PublishCheck publishCheck;
    private final Forges forges;
    private final Git git;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public DeliverDraft(TaskStore tasks, DeliveryStore deliveries, EventLog events, WorkerContext context, PublishCheck publishCheck, Forges forges,
            Git git, Clock clock, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.deliveries = deliveries;
        this.events = events;
        this.context = context;
        this.publishCheck = publishCheck;
        this.forges = forges;
        this.git = git;
        this.clock = clock;
        this.transaction = transaction;
    }

    public DeliveredDraft deliver(TaskReference reference, Actor actor) {
        Workplace workplace = transaction.execute(_ -> context.of(tasks.get(reference).id()));
        RegisteredForge forge = requireADraftReviewShip(workplace);
        String head = requireTheCopy(workplace);
        requireACleanPublication(workplace);
        Forge serving = forges.serving(forge);
        Optional<MergeRequestView> open = serving.openMergeRequestFrom(forge, workplace.dispatch().branch());
        MergeRequestView draft = open.orElseGet(() -> serving.openDraft(forge, proposalFor(workplace)));
        requireTheDeliveryOf(workplace, draft, head);
        return recorded(workplace, draft, open.isEmpty(), actor);
    }

    private static RegisteredForge requireADraftReviewShip(Workplace workplace) {
        if (!workplace.isAShip() || workplace.delivery() != DeliveryMode.DRAFT_REVIEW) {
            throw new Refusal(workplace.task().id() + " is delivered " + workplace.delivery().word()
                    + "; edi deliver opens drafts for ships delivered draft-review only.");
        }
        return workplace.project().requireForge();
    }

    private String requireTheCopy(Workplace workplace) {
        if (!Files.isDirectory(workplace.worktree())) {
            throw new Refusal("The isolated copy " + workplace.worktree() + " is gone, so EDI has no head to deliver.");
        }
        return git.head(workplace.worktree());
    }

    private void requireACleanPublication(Workplace workplace) {
        RevisionRange outgoing = new RevisionRange(workplace.baseRef() + "..HEAD");
        PublishVerdict verdict = publishCheck.checkPush(workplace.worktree(), outgoing, git.pushUrlOf(workplace.worktree(), ORIGIN));
        if (!verdict.allowsPush()) {
            throw new Refusal("The publish check refuses " + workplace.dispatch().branch() + ": "
                    + String.join("; ", verdict.findings().stream().map(Object::toString).toList()) + ".");
        }
    }

    private DraftProposal proposalFor(Workplace workplace) {
        return DraftProposal.forWork(workplace.dispatch().branch(), workplace.base(), workplace.task().intake().title(), deliveryNoteOf(workplace));
    }

    private static String deliveryNoteOf(Workplace workplace) {
        Path report = workplace.taskDirectory().resolve(REPORT_FILE);
        try {
            return Files.readString(report, StandardCharsets.UTF_8);
        } catch (NoSuchFileException absent) {
            return workplace.task().intake().title() + ".";
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static void requireTheDeliveryOf(Workplace workplace, MergeRequestView draft, String head) {
        List<String> gaps = draft.gapsAsTheDeliveryOf(workplace.dispatch().branch(), workplace.base(), head);
        if (!gaps.isEmpty()) {
            throw new Refusal("EDI records no delivery of " + workplace.task().id() + ": " + String.join("; ", gaps) + ".");
        }
    }

    private DeliveredDraft recorded(Workplace workplace, MergeRequestView draft, boolean opened, Actor actor) {
        return transaction.execute(_ -> {
            Instant now = clock.now();
            Delivery delivery = Delivery.of(workplace.task().id(), draft, now);
            deliveries.save(delivery);
            DomainEvent event = opened ? new DraftOpened(delivery.task(), draft.address()) : new DraftRecorded(delivery.task(), draft.address());
            events.record(event, actor, now);
            return new DeliveredDraft(delivery, opened);
        });
    }

    public record DeliveredDraft(Delivery delivery, boolean opened) {
    }
}
