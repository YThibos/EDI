package io.github.ythibos.edi.application.dispatch;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.brief.BriefFacts;
import io.github.ythibos.edi.application.brief.WrittenBriefs;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.domain.worker.Worker;

@Service
public class RewriteBrief {

    private final TaskStore tasks;
    private final DispatchStore dispatches;
    private final WorkerStore workers;
    private final ProjectRegistry registry;
    private final GatherBriefFacts briefFacts;
    private final WrittenBriefs writtenBriefs;
    private final TransactionTemplate transaction;

    public RewriteBrief(TaskStore tasks, DispatchStore dispatches, WorkerStore workers, ProjectRegistry registry, GatherBriefFacts briefFacts,
            WrittenBriefs writtenBriefs, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.dispatches = dispatches;
        this.workers = workers;
        this.registry = registry;
        this.briefFacts = briefFacts;
        this.writtenBriefs = writtenBriefs;
        this.transaction = transaction;
    }

    public void afterANoteOn(TaskId task) {
        transaction.execute(_ -> briefedFactsOf(task)).ifPresent(writtenBriefs::written);
    }

    private Optional<BriefFacts> briefedFactsOf(TaskId id) {
        Task task = tasks.get(id);
        if (task.state() != TaskState.IN_FLIGHT || !writtenBriefs.isWrittenFor(id)) {
            return Optional.empty();
        }
        return workers.latestOf(id).map(Worker::id).flatMap(worker -> dispatches.of(id)
                .map(dispatch -> briefFacts.factsFor(task, registry.entryOf(task.intake().projects().getFirst()), dispatch, worker)));
    }
}
