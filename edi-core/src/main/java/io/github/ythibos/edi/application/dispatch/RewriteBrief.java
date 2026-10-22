package io.github.ythibos.edi.application.dispatch;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.brief.BriefFacts;
import io.github.ythibos.edi.application.brief.WrittenBriefs;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.domain.dispatch.Dispatch;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Service
public class RewriteBrief {

    private final EdiHome home;
    private final TaskStore tasks;
    private final DispatchStore dispatches;
    private final WorkerStore workers;
    private final ProjectRegistry registry;
    private final MachineProfile machine;
    private final ResearchBeforeImplementation research;
    private final WrittenBriefs writtenBriefs;
    private final WorkerRuntime runtime;
    private final TransactionTemplate transaction;

    public RewriteBrief(EdiHome home, TaskStore tasks, DispatchStore dispatches, WorkerStore workers, ProjectRegistry registry, MachineProfile machine,
            ResearchBeforeImplementation research, WrittenBriefs writtenBriefs, WorkerRuntime runtime, TransactionTemplate transaction) {
        this.home = home;
        this.tasks = tasks;
        this.dispatches = dispatches;
        this.workers = workers;
        this.registry = registry;
        this.machine = machine;
        this.research = research;
        this.writtenBriefs = writtenBriefs;
        this.runtime = runtime;
        this.transaction = transaction;
    }

    public void afterANoteOn(TaskId task) {
        transaction.execute(_ -> briefedWorkOn(task)).map(this::briefFacts).ifPresent(writtenBriefs::written);
    }

    private Optional<BriefedWork> briefedWorkOn(TaskId id) {
        Task task = tasks.get(id);
        if (task.state() != TaskState.IN_FLIGHT || !writtenBriefs.isWrittenFor(id)) {
            return Optional.empty();
        }
        return workers.latestOf(id).map(Worker::id).flatMap(worker -> dispatches.of(id).map(dispatch -> new BriefedWork(task, worker, dispatch)));
    }

    private BriefFacts briefFacts(BriefedWork work) {
        Task task = work.task();
        ProjectEntry project = registry.entryOf(task.intake().projects().getFirst());
        BaseBranch base = task.intake().base() == null ? project.requireBase() : task.intake().base();
        return new BriefFacts(task, work.worker(), project, work.dispatch(), base, home.worktreeOf(project.project().name(), task.id()),
                home.taskDirectory(task.id()), runtime.socket(), writtenBriefs.craftRulesFor(project), research.planFor(task.id()).orElse(null),
                machine.time().requireWorkedLine());
    }

    private record BriefedWork(Task task, WorkerId worker, Dispatch dispatch) {
    }
}
