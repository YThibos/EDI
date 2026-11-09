package io.github.ythibos.edi.application.worker;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.InboxStore;
import io.github.ythibos.edi.adapter.store.PipelineRunStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.decision.TaskReference;

@Service
public class WorkerDetailQuery {

    private final TaskStore tasks;
    private final WorkerStore workers;
    private final InboxStore inbox;
    private final PipelineRunStore pipelineRuns;
    private final TransactionTemplate transaction;

    public WorkerDetailQuery(TaskStore tasks, WorkerStore workers, InboxStore inbox, PipelineRunStore pipelineRuns, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.workers = workers;
        this.inbox = inbox;
        this.pipelineRuns = pipelineRuns;
        this.transaction = transaction;
    }

    public WorkerDetail detail(TaskReference reference) {
        return transaction.execute(_ -> detailOf(tasks.get(reference).id()));
    }

    private WorkerDetail detailOf(TaskId task) {
        return new WorkerDetail(
                workers.latestOf(task).orElseThrow(() -> new Refusal(Rule.TASK_DISPATCHED, task + " has had no worker; dispatch it first.")),
                inbox.allOf(task),
                pipelineRuns.latestOf(task).orElse(null));
    }
}
