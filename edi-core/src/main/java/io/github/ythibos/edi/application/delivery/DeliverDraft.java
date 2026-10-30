package io.github.ythibos.edi.application.delivery;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.store.TaskStore;
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
import io.github.ythibos.edi.domain.publish.RequestText;
import io.github.ythibos.edi.domain.publish.RevisionRange;
import io.github.ythibos.edi.domain.publish.TextVerdict;
import io.github.ythibos.edi.domain.task.TaskId;

@Service
public class DeliverDraft {

    private static final String ORIGIN = "origin";

    private final TaskStore tasks;
    private final WorkerContext context;
    private final PublishCheck publishCheck;
    private final Forges forges;
    private final DeliveryRecords records;
    private final Git git;
    private final TransactionTemplate transaction;

    public DeliverDraft(TaskStore tasks, WorkerContext context, PublishCheck publishCheck, Forges forges, DeliveryRecords records, Git git,
            TransactionTemplate transaction) {
        this.tasks = tasks;
        this.context = context;
        this.publishCheck = publishCheck;
        this.forges = forges;
        this.records = records;
        this.git = git;
        this.transaction = transaction;
    }

    public DeliveredDraft deliver(TaskReference reference, Actor actor) {
        Workplace workplace = transaction.execute(_ -> context.of(tasks.get(reference).id()));
        RegisteredForge forge = requireADraftReviewShip(workplace);
        String head = requireTheCopy(workplace);
        requireACleanPublication(workplace);
        Optional<MergeRequestView> found = records.requestFrom(workplace);
        requireNoMergeYet(workplace, found);
        MergeRequestView request = found.orElseGet(() -> forges.serving(forge).openDraft(forge, aCleanProposalFor(workplace)));
        found.ifPresent(posted -> requireACleanText(workplace, forges.serving(forge).textOf(forge, posted.address())));
        requireTheDeliveryOf(workplace, request, head);
        return recorded(workplace, request, found.isEmpty(), actor);
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
            throw new Refusal("The isolated copy " + workplace.worktree() + " is gone, so there is no head to deliver.");
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

    private static void requireNoMergeYet(Workplace workplace, Optional<MergeRequestView> found) {
        found.filter(MergeRequestView::isMerged).ifPresent(merged -> {
            throw new Refusal(merged.address() + " is merged already; edi merged " + workplace.task().id() + " follows its merge.");
        });
    }

    private DraftProposal aCleanProposalFor(Workplace workplace) {
        DraftProposal proposal = DraftProposal.forWork(workplace.dispatch().branch(), workplace.base(), workplace.task().intake().title(),
                deliveryNoteOf(workplace));
        requireACleanText(workplace, new RequestText(proposal.title(), proposal.description()));
        return proposal;
    }

    private void requireACleanText(Workplace workplace, RequestText text) {
        TextVerdict verdict = publishCheck.checkText(workplace.worktree(), text);
        if (!verdict.allowsPosting()) {
            throw new Refusal(verdict.refusal());
        }
    }

    private static String deliveryNoteOf(Workplace workplace) {
        try {
            return Files.readString(workplace.report(), StandardCharsets.UTF_8);
        } catch (NoSuchFileException absent) {
            return workplace.task().intake().title() + ".";
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static void requireTheDeliveryOf(Workplace workplace, MergeRequestView draft, String head) {
        List<String> gaps = draft.gapsAsTheDeliveryOf(workplace.dispatch().branch(), workplace.base(), head);
        if (!gaps.isEmpty()) {
            throw new Refusal("No delivery of " + workplace.task().id() + " is recorded: " + String.join("; ", gaps) + ".");
        }
    }

    private DeliveredDraft recorded(Workplace workplace, MergeRequestView request, boolean opened, Actor actor) {
        TaskId task = workplace.task().id();
        DomainEvent event = opened ? new DraftOpened(task, request.address()) : new DraftRecorded(task, request.address());
        return transaction.execute(_ -> new DeliveredDraft(records.recorded(task, request, event, actor), opened));
    }

    public record DeliveredDraft(Delivery delivery, boolean opened) {
    }
}
