package io.github.ythibos.edi.web.task;

import java.util.List;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.task.TaskList;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.web.text.Table;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.Words;

@Component
class TaskListText implements TextPresenter<TaskList> {

    private static final String HELD = "[held]";

    @Override
    public Class<TaskList> presents() {
        return TaskList.class;
    }

    @Override
    public String text(TaskList list) {
        Table table = new Table("  ");
        for (TaskState state : list.states()) {
            List<Task> inState = list.tasks().stream().filter(task -> task.state() == state).toList();
            table.line(Words.counted(headingOf(state), inState.size()));
            inState.forEach(task -> table.row(rowOf(task)));
        }
        return table.text();
    }

    static String headingOf(TaskState state) {
        return switch (state) {
            case IN_FLIGHT -> "In flight";
            case QUEUED -> "Queued";
            case DONE -> "Done";
            case DROPPED -> "Dropped";
        };
    }

    private static String[] rowOf(Task task) {
        String[] cells = TaskCells.of(task);
        if (task.isHeld()) {
            cells[cells.length - 1] = cells[cells.length - 1] + " " + HELD;
        }
        return cells;
    }
}
