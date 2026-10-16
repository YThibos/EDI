package io.github.ythibos.edi.web.task;

import static io.github.ythibos.edi.web.RequestValues.each;
import static io.github.ythibos.edi.web.RequestValues.optional;
import static io.github.ythibos.edi.web.RequestValues.orEmpty;
import static io.github.ythibos.edi.web.RequestValues.required;

import java.util.List;

import io.github.ythibos.edi.application.task.NewTask;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;

record AddTaskRequest(
        String id,
        String kind,
        List<String> projects,
        String title,
        String key,
        String base,
        List<String> blockedBy,
        String body) {

    NewTask newTask() {
        return new NewTask(optional(id, TaskId::new), intake(), orEmpty(body));
    }

    private Intake intake() {
        return new Intake(
                TaskKind.named(required(kind, "kind")),
                required(title, "title"),
                each(projects, ProjectName::new),
                optional(key, WorkKey::parse),
                optional(base, BaseBranch::new),
                each(blockedBy, TaskId::new));
    }
}
