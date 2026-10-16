package io.github.ythibos.edi.application.delivery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.store.CloneRefreshStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.delivery.CloneRefresh;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.CloneRefreshed;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;

@Service
public class CloneRefreshes {

    private static final Logger LOG = LoggerFactory.getLogger(CloneRefreshes.class);

    private final CloneRefreshStore refreshes;
    private final ProjectRegistry registry;
    private final Git git;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public CloneRefreshes(CloneRefreshStore refreshes, ProjectRegistry registry, Git git, EventLog events, Clock clock,
            TransactionTemplate transaction) {
        this.refreshes = refreshes;
        this.registry = registry;
        this.git = git;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public void awaitTheMergeOf(Task task, MergeRequestView merge, Instant at) {
        refreshes.save(CloneRefresh.awaiting(task.id(), task.intake().projects().getFirst(), new BaseBranch(merge.targetBranch()),
                merge.landedCommit(), at));
    }

    public void refreshEveryClone() {
        transaction.execute(_ -> refreshes.pending()).stream().map(CloneRefresh::project).distinct().forEach(this::refreshAfterAFetch);
    }

    public Optional<CloneRefresh> after(TaskId task) {
        return transaction.execute(_ -> refreshes.after(task));
    }

    public List<CloneRefresh> awaitingCleanUp() {
        return transaction.execute(_ -> refreshes.verifiedAwaitingCleanUp());
    }

    public void cleanUpTried(CloneRefresh refresh) {
        transaction.executeWithoutResult(_ -> refreshes.save(refresh.cleanUpTried(clock.now())));
    }

    public boolean isFresh(ProjectName project) {
        try {
            requireFresh(project);
            return true;
        } catch (Refusal | ProgramFailure stale) {
            return false;
        }
    }

    public void requireFresh(ProjectName project) {
        List<CloneRefresh> pending = transaction.execute(_ -> refreshes.pendingFor(project));
        if (pending.isEmpty()) {
            return;
        }
        git.fetchOrigin(registry.entryOf(project).projectClone());
        pending.stream().filter(refresh -> !verified(refresh)).findFirst().ifPresent(waiting -> {
            throw new Refusal("Follow-up work on " + project + " waits until its clone holds commit " + waiting.expectedCommit() + " on "
                    + waiting.baseRef() + ", which the merge of " + waiting.task() + " landed; EDI fetches again on the next dispatch.");
        });
    }

    private void refreshAfterAFetch(ProjectName project) {
        try {
            git.fetchOrigin(registry.entryOf(project).projectClone());
            transaction.execute(_ -> refreshes.pendingFor(project)).forEach(this::verified);
        } catch (ProgramFailure unreachable) {
            LOG.warn("The clone of {} could not fetch the merges it waits for: {}", project, unreachable.getMessage());
        }
    }

    private boolean verified(CloneRefresh refresh) {
        if (!git.isAncestor(registry.entryOf(refresh.project()).projectClone(), refresh.expectedCommit(), refresh.baseRef())) {
            return false;
        }
        transaction.executeWithoutResult(_ -> {
            Instant now = clock.now();
            refreshes.save(refresh.verified(now));
            events.record(new CloneRefreshed(refresh.task(), refresh.project(), refresh.expectedCommit()), Actor.CORE, now);
        });
        return true;
    }
}
