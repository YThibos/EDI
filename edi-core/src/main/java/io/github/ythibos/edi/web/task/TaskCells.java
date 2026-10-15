package io.github.ythibos.edi.web.task;

import java.util.stream.Collectors;

import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.Task;

final class TaskCells {

    private static final String NONE = "-";
    private static final String PROJECT_SET_JOINER = " + ";

    private TaskCells() {
    }

    static String[] of(Task task) {
        return new String[] {task.id().value(), task.intake().kind().word(), projectsOf(task), workKeyOf(task), task.intake().title()};
    }

    static String projectsOf(Task task) {
        return task.intake().projects().isEmpty()
                ? NONE
                : task.intake().projects().stream().map(ProjectName::value).collect(Collectors.joining(PROJECT_SET_JOINER));
    }

    private static String workKeyOf(Task task) {
        return task.intake().workKey() == null ? NONE : task.intake().workKey().toString();
    }
}
