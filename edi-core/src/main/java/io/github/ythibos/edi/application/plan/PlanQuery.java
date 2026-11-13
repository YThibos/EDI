package io.github.ythibos.edi.application.plan;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.PlanStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.domain.plan.Plan;
import io.github.ythibos.edi.domain.plan.Slice;
import io.github.ythibos.edi.domain.plan.SliceStanding;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.Task;

@Service
public class PlanQuery {

    private final PlanStore plans;
    private final TaskStore tasks;
    private final TransactionTemplate transaction;

    public PlanQuery(PlanStore plans, TaskStore tasks, TransactionTemplate transaction) {
        this.plans = plans;
        this.tasks = tasks;
        this.transaction = transaction;
    }

    public PlanStandings standings(WorkKey key) {
        return transaction.execute(_ -> {
            Plan plan = plans.get(key);
            return new PlanStandings(plan, standingsOf(plan));
        });
    }

    private List<SliceStanding> standingsOf(Plan plan) {
        return plan.slices().stream().map(this::standingOf).toList();
    }

    private SliceStanding standingOf(Slice slice) {
        Task task = tasks.get(slice.task());
        return new SliceStanding(slice, task, tasks.waitsOf(task));
    }
}
