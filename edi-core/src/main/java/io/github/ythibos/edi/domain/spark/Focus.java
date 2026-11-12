package io.github.ythibos.edi.domain.spark;

import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.TaskId;

public record Focus(TaskId task, ProjectName project) {

    public static final Focus NONE = new Focus(null, null);

    public Focus {
        if (task != null && project != null) {
            throw new IllegalArgumentException("A spark is focused on a task or on a project, never on both.");
        }
    }

    public static Focus onTask(TaskId task) {
        return new Focus(task, null);
    }

    public static Focus onProject(ProjectName project) {
        return new Focus(null, project);
    }
}
