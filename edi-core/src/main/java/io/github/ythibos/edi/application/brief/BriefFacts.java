package io.github.ythibos.edi.application.brief;

import java.nio.file.Path;
import java.util.Objects;

import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.domain.dispatch.Dispatch;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.worker.WorkerId;

public record BriefFacts(
        Task task,
        WorkerId worker,
        ProjectEntry project,
        Dispatch dispatch,
        BaseBranch base,
        Path worktree,
        Path taskDirectory,
        String ediSocket,
        String craftRules) {

    public BriefFacts {
        Objects.requireNonNull(task, "A brief is for a task.");
        Objects.requireNonNull(worker, "A brief is for a worker.");
        Objects.requireNonNull(project, "A brief names the project's rules.");
        Objects.requireNonNull(dispatch, "A brief follows the task's dispatch.");
        Objects.requireNonNull(base, "A brief names the base branch.");
        if (project.craftRules() && (craftRules == null || craftRules.isBlank())) {
            throw new IllegalArgumentException(project.project().name() + " follows the craft rules, so its brief carries their text.");
        }
    }

    public Path reportPath() {
        return taskDirectory.resolve("report.md");
    }

    public Path intentPath() {
        return taskDirectory.resolve("intent.md");
    }

    public DeliveryMode delivery() {
        return task.intake().deliveryOr(project.delivery());
    }
}
