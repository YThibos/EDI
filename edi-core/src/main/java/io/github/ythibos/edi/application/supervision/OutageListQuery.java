package io.github.ythibos.edi.application.supervision;

import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.InboxStore;
import io.github.ythibos.edi.adapter.store.OutageStore;
import io.github.ythibos.edi.adapter.store.RecoveryCostStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.supervision.OutageList.UnpushedWork;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.recovery.OutageEntry;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.worker.Worker;

@Service
public class OutageListQuery {

    private final OutageStore outages;
    private final TaskStore tasks;
    private final WorkerStore workers;
    private final DispatchStore dispatches;
    private final DecisionStore decisions;
    private final InboxStore inbox;
    private final RecoveryCostStore recoveryCosts;
    private final CurrentRoutes routes;
    private final Git git;
    private final EdiHome home;
    private final TransactionTemplate transaction;

    public OutageListQuery(OutageStore outages, TaskStore tasks, WorkerStore workers, DispatchStore dispatches, DecisionStore decisions,
            InboxStore inbox, RecoveryCostStore recoveryCosts, CurrentRoutes routes, Git git, EdiHome home, TransactionTemplate transaction) {
        this.outages = outages;
        this.tasks = tasks;
        this.workers = workers;
        this.dispatches = dispatches;
        this.decisions = decisions;
        this.inbox = inbox;
        this.recoveryCosts = recoveryCosts;
        this.routes = routes;
        this.git = git;
        this.home = home;
        this.transaction = transaction;
    }

    public OutageList list() {
        List<OutageList.Entry> entries = transaction.execute(_ -> outages.open().stream().map(this::entryOf).toList());
        return new OutageList(entries.stream().map(this::withUnpushedWork).toList(), transaction.execute(_ -> recoveryCosts.all()));
    }

    private OutageList.Entry entryOf(OutageEntry entry) {
        Task task = tasks.get(entry.task());
        Worker worker = workers.get(entry.worker()).orElseThrow();
        int unread = (int) inbox.unacknowledgedOf(task.id()).stream().filter(message -> !message.isReceived()).count();
        return new OutageList.Entry(entry, task, worker, routes.of(task, dispatches.of(task.id()).orElseThrow()),
                decisions.ofTask(task.id()).filter(Decision::isOpen).map(Decision::number).orElse(null), unread, UnpushedWork.UNVERIFIED,
                home.worktreeOf(task.intake().projects().getFirst(), task.id()));
    }

    private OutageList.Entry withUnpushedWork(OutageList.Entry entry) {
        return new OutageList.Entry(entry.entry(), entry.task(), entry.worker(), entry.routeNow(), entry.openDecision(), entry.unreadMessages(),
                unpushedWorkIn(entry.worktree()), entry.worktree());
    }

    private UnpushedWork unpushedWorkIn(Path worktree) {
        try {
            return new UnpushedWork(true, git.commitsOnNoRemote(worktree), git.hasUncommittedChanges(worktree));
        } catch (ProgramFailure unreadable) {
            return UnpushedWork.UNVERIFIED;
        }
    }
}
