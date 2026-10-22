package io.github.ythibos.edi.application.delivery;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.worker.WorkerContext;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.delivery.SnapshotVersions;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.DeliveryRefused;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.publish.AddedLine;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Component
public class DoneClaims {

    private final WorkerContext context;
    private final Git git;
    private final DeliveryRecords records;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public DoneClaims(WorkerContext context, Git git, DeliveryRecords records, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.context = context;
        this.git = git;
        this.records = records;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public Optional<MergeRequestView> verify(WorkerId worker) {
        Workplace workplace = transaction.execute(_ -> context.of(worker.task()));
        if (!workplace.isAShip()) {
            return Optional.empty();
        }
        requireTheCopy(workplace);
        Optional<MergeRequestView> request = requestOf(workplace);
        List<String> gaps = new ArrayList<>(snapshotGapsOf(workplace));
        if (workplace.delivery() == DeliveryMode.DRAFT_REVIEW) {
            gaps.addAll(requestGapsOf(workplace, request));
        }
        if (!gaps.isEmpty()) {
            refuse(worker, gaps);
        }
        return request;
    }

    private static void requireTheCopy(Workplace workplace) {
        if (!Files.isDirectory(workplace.worktree())) {
            throw new Refusal("The isolated copy " + workplace.worktree() + " is gone, so EDI cannot check what the done claims.");
        }
    }

    private Optional<MergeRequestView> requestOf(Workplace workplace) {
        return records.followsARequest(workplace) ? records.requestFrom(workplace) : Optional.empty();
    }

    private List<String> snapshotGapsOf(Workplace workplace) {
        List<AddedLine> snapshots = SnapshotVersions.addedIn(git.buildFileLinesAddedSince(workplace.worktree(), workplace.baseRef()));
        return snapshots.isEmpty() ? List.of() : List.of(SnapshotVersions.refusalOf(snapshots));
    }

    private List<String> requestGapsOf(Workplace workplace, Optional<MergeRequestView> request) {
        if (workplace.project().forge() == null) {
            return List.of(workplace.project().project().name() + " registers no forge, so no merge request can carry this work");
        }
        return request.map(found -> found.gapsAsTheDeliveryOf(workplace.dispatch().branch(), workplace.base(), git.head(workplace.worktree())))
                .orElseGet(() -> List.of(records.noRequestFrom(workplace)
                        + "; the pipeline opens it as a draft, and edi deliver opens it when the pipeline cannot"));
    }

    private void refuse(WorkerId worker, List<String> gaps) {
        transaction.executeWithoutResult(_ -> events.record(new DeliveryRefused(worker.task(), worker, gaps), Actor.CORE, clock.now()));
        throw new Refusal("EDI accepts this done once its claims hold: " + String.join("; ", gaps) + ".");
    }
}
