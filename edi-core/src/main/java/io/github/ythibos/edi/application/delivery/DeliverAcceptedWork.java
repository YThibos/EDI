package io.github.ythibos.edi.application.delivery;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.application.worker.WorkerContext;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.task.TaskId;

@Service
public class DeliverAcceptedWork {

    private final WorkerContext context;
    private final DeliveryRecords records;
    private final FollowMerges merges;
    private final CaptainCheckouts checkouts;
    private final CleanUpTask cleanUp;
    private final TransactionTemplate transaction;

    public DeliverAcceptedWork(WorkerContext context, DeliveryRecords records, FollowMerges merges, CaptainCheckouts checkouts, CleanUpTask cleanUp,
            TransactionTemplate transaction) {
        this.context = context;
        this.records = records;
        this.merges = merges;
        this.checkouts = checkouts;
        this.cleanUp = cleanUp;
        this.transaction = transaction;
    }

    public void deliver(TaskId task) {
        deliver(task, Optional.empty());
    }

    public void deliver(TaskId task, Optional<MergeRequestView> claimedRequest) {
        Workplace workplace = transaction.execute(_ -> context.of(task));
        if (!workplace.isAShip()) {
            return;
        }
        switch (workplace.delivery()) {
            case DRAFT_REVIEW -> merges.followTheAcceptedRequest(task, claimedRequest.orElseGet(() -> records.requireTheRequestFrom(workplace)));
            case LOCAL_BRANCH -> checkouts.deliverTheBranch(workplace);
            case LOCAL_APPLY -> checkouts.applyTheChange(workplace);
            case REPORT -> cleanUp.cleanUp(new TaskReference.ById(task), Actor.CORE);
        }
    }
}
