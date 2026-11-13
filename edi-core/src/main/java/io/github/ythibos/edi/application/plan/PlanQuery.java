package io.github.ythibos.edi.application.plan;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.PlanStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.domain.plan.Plan;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;

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
            return new PlanStandings(plan, plan.standings(tasksOf(plan)));
        });
    }

    private Map<TaskId, Task> tasksOf(Plan plan) {
        return plan.tasks().stream().map(tasks::get).collect(Collectors.toMap(Task::id, Function.identity()));
    }
}
