package io.github.ythibos.edi.web.task;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.task.AddTask;
import io.github.ythibos.edi.application.task.NoteTask;
import io.github.ythibos.edi.application.task.TaskDetail;
import io.github.ythibos.edi.application.task.TaskDetailQuery;
import io.github.ythibos.edi.application.task.TaskList;
import io.github.ythibos.edi.application.task.TaskListQuery;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.web.Confirmation;

@RestController
@RequestMapping("/api/tasks")
class TaskController {

    private final TaskListQuery list;
    private final TaskDetailQuery detail;
    private final AddTask addTask;
    private final NoteTask noteTask;

    TaskController(TaskListQuery list, TaskDetailQuery detail, AddTask addTask, NoteTask noteTask) {
        this.list = list;
        this.detail = detail;
        this.addTask = addTask;
        this.noteTask = noteTask;
    }

    @GetMapping
    TaskList tasks(@RequestParam(required = false) String state) {
        return state == null ? list.openWork() : list.inState(TaskState.named(state));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Confirmation add(@RequestBody AddTaskRequest request) {
        return new Confirmation.Queued(addTask.add(request.newTask(), Actor.PRIMARY));
    }

    @GetMapping("/{reference}")
    TaskDetail task(@PathVariable String reference) {
        return detail.detail(TaskReference.parse(reference));
    }

    @PostMapping("/{reference}/notes")
    Confirmation note(@PathVariable String reference, @RequestBody NoteRequest request) {
        return new Confirmation.Noted(noteTask.note(TaskReference.parse(reference), request.text(), Actor.PRIMARY));
    }
}
