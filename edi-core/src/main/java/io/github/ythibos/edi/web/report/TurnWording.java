package io.github.ythibos.edi.web.report;

import java.util.List;
import java.util.Map;

import io.github.ythibos.edi.domain.report.TaskWording;
import io.github.ythibos.edi.domain.report.Turn;
import io.github.ythibos.edi.domain.task.Task;

final class TurnWording {

    private final Map<String, TaskWording> wordings;

    TurnWording(Turn turn) {
        this.wordings = turn.tasks();
    }

    String textOf(Task task) {
        String text = wordingOf(task).text();
        return text == null ? task.intake().title() : text;
    }

    String textWithStateOf(Task task) {
        String state = wordingOf(task).state();
        return state == null ? textOf(task) : textOf(task) + " (" + state + ")";
    }

    ProjectSet projectsOf(Task task) {
        List<String> projects = wordingOf(task).projects();
        return projects == null ? ProjectSet.of(task.intake().projects()) : new ProjectSet(projects);
    }

    private TaskWording wordingOf(Task task) {
        return wordings.getOrDefault(task.id().value(), TaskWording.NONE);
    }
}
