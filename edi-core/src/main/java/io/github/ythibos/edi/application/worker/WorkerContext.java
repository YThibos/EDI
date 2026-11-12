package io.github.ythibos.edi.application.worker;

import java.nio.file.Path;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.dispatch.Dispatch;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;

@Component
public class WorkerContext {

    private final EdiHome home;
    private final TaskStore tasks;
    private final DispatchStore dispatches;
    private final ProjectRegistry registry;

    public WorkerContext(EdiHome home, TaskStore tasks, DispatchStore dispatches, ProjectRegistry registry) {
        this.home = home;
        this.tasks = tasks;
        this.dispatches = dispatches;
        this.registry = registry;
    }

    public Workplace of(TaskId id) {
        Task task = tasks.get(id);
        ProjectEntry project = registry.entryOf(task.intake().projects().getFirst());
        Dispatch dispatch = dispatches.of(id).orElseThrow(() -> new Refusal(Rule.TASK_DISPATCHED, id + " was never dispatched."));
        BaseBranch base = task.intake().base() == null ? project.requireBase() : task.intake().base();
        return new Workplace(task, project, dispatch, base, home.worktreeOf(project.project().name(), id), home.taskDirectory(id));
    }

    public record Workplace(Task task, ProjectEntry project, Dispatch dispatch, BaseBranch base, Path worktree, Path taskDirectory) {

        private static final String REPORT_FILE = "report.md";

        public String baseRef() {
            return "origin/" + base;
        }

        public DeliveryMode delivery() {
            return task.intake().deliveryOr(project.delivery());
        }

        public TaskKind kind() {
            return task.intake().kind();
        }

        public boolean isAShip() {
            return kind() == TaskKind.SHIP;
        }

        public Path report() {
            return taskDirectory.resolve(REPORT_FILE);
        }
    }
}
