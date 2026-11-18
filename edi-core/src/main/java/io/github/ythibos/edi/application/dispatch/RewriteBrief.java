package io.github.ythibos.edi.application.dispatch;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.home.InvalidConfiguration;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.brief.BriefFacts;
import io.github.ythibos.edi.application.brief.WrittenBriefs;
import io.github.ythibos.edi.application.supervision.CurrentRoutes;
import io.github.ythibos.edi.domain.dispatch.ChosenRoute;
import io.github.ythibos.edi.domain.dispatch.Dispatch;
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
    private final CurrentRoutes routes;
    private final WrittenBriefs writtenBriefs;
    private final TransactionTemplate transaction;

    public RewriteBrief(TaskStore tasks, DispatchStore dispatches, WorkerStore workers, ProjectRegistry registry, GatherBriefFacts briefFacts,
            CurrentRoutes routes, WrittenBriefs writtenBriefs, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.dispatches = dispatches;
        this.workers = workers;
        this.registry = registry;
        this.briefFacts = briefFacts;
        this.routes = routes;
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
        return workers.latestOf(id).flatMap(worker -> dispatches.of(id)
                .map(dispatch -> briefFacts.factsFor(task, registry.entryOf(task.intake().projects().getFirst()), dispatch,
                        launchedRouteOf(worker, task, dispatch), worker.id())));
    }

    private ChosenRoute launchedRouteOf(Worker worker, Task task, Dispatch dispatch) {
        return currentChoiceFor(task, dispatch)
                .filter(chosen -> chosen.route().equals(worker.route()))
                .orElseGet(() -> ChosenRoute.asAtItsLaunch(worker.route()));
    }

    private Optional<ChosenRoute> currentChoiceFor(Task task, Dispatch dispatch) {
        try {
            return Optional.of(routes.chosenFor(task, dispatch));
        } catch (InvalidConfiguration unloadable) {
            return Optional.empty();
        }
    }
}
