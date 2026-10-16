package io.github.ythibos.edi.application.worker;

import java.nio.file.Path;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent;
import io.github.ythibos.edi.domain.event.DomainEvent.BranchDeliveredToCaptain;
import io.github.ythibos.edi.domain.event.DomainEvent.BranchDeliveryFailed;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;

@Component
public class CaptainCheckouts {

    private final WorkerContext context;
    private final Git git;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public CaptainCheckouts(WorkerContext context, Git git, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.context = context;
        this.git = git;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public void deliverWhenLocal(TaskId task) {
        Workplace workplace = transaction.execute(_ -> context.of(task));
        if (workplace.task().intake().kind() != TaskKind.SHIP || workplace.project().delivery() != DeliveryMode.LOCAL_BRANCH) {
            return;
        }
        record(delivered(workplace));
    }

    private DomainEvent delivered(Workplace workplace) {
        Path checkout = workplace.project().captainCheckout();
        if (checkout == null) {
            return new BranchDeliveryFailed(workplace.task().id(), workplace.dispatch().branch(),
                    workplace.project().project().name() + " names no captainCheckout in the project registry");
        }
        try {
            git.fetchBranchInto(checkout, workplace.worktree(), workplace.dispatch().branch());
            return new BranchDeliveredToCaptain(workplace.task().id(), workplace.dispatch().branch());
        } catch (ProgramFailure refused) {
            return new BranchDeliveryFailed(workplace.task().id(), workplace.dispatch().branch(), refused.getMessage());
        }
    }

    private void record(DomainEvent delivery) {
        transaction.executeWithoutResult(_ -> events.record(delivery, Actor.CORE, clock.now()));
    }
}
