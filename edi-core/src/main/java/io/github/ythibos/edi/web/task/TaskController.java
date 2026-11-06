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

import io.github.ythibos.edi.application.delivery.DropTask;
import io.github.ythibos.edi.application.migration.AdoptForkTask;
import io.github.ythibos.edi.application.task.AddTask;
import io.github.ythibos.edi.application.task.NoteTask;
import io.github.ythibos.edi.application.task.PromoteScout;
import io.github.ythibos.edi.application.task.TaskDetail;
import io.github.ythibos.edi.application.task.TaskDetailQuery;
import io.github.ythibos.edi.application.task.TaskList;
import io.github.ythibos.edi.application.task.TaskListQuery;
import io.github.ythibos.edi.application.tracker.KeyTask;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.web.Confirmation;
import io.github.ythibos.edi.web.access.Caller;

@RestController
@RequestMapping("/api/tasks")
class TaskController {

    private final TaskListQuery list;
    private final TaskDetailQuery detail;
    private final AddTask addTask;
    private final NoteTask noteTask;
    private final PromoteScout promoteScout;
    private final DropTask dropTask;
    private final KeyTask keyTask;
    private final AdoptForkTask adoptForkTask;

    TaskController(TaskListQuery list, TaskDetailQuery detail, AddTask addTask, NoteTask noteTask, PromoteScout promoteScout, DropTask dropTask,
            KeyTask keyTask, AdoptForkTask adoptForkTask) {
        this.list = list;
        this.detail = detail;
        this.addTask = addTask;
        this.noteTask = noteTask;
        this.promoteScout = promoteScout;
        this.dropTask = dropTask;
        this.keyTask = keyTask;
        this.adoptForkTask = adoptForkTask;
    }

    @GetMapping
    TaskList tasks(@RequestParam(required = false) String state) {
        return state == null ? list.openWork() : list.inState(TaskState.named(state));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Confirmation add(@RequestBody AddTaskRequest request, Caller caller) {
        caller.requirePrimary("edi task add");
        return new Confirmation.Queued(addTask.add(request.newTask(), caller.actor()));
    }

    @GetMapping("/{reference}")
    TaskDetail task(@PathVariable String reference, Caller caller) {
        caller.requireThePrimaryOrTheCaptain("A task's whole record");
        return detail.detail(TaskReference.parse(reference));
    }

    @PostMapping("/{reference}/notes")
    Confirmation note(@PathVariable String reference, @RequestBody NoteRequest request, Caller caller) {
        caller.requirePrimary("edi task note");
        return new Confirmation.Noted(noteTask.note(TaskReference.parse(reference), request.text(), caller.actor()));
    }

    @PostMapping("/{reference}/promote")
    @ResponseStatus(HttpStatus.CREATED)
    Confirmation promote(@PathVariable String reference, @RequestBody PromoteRequest request, Caller caller) {
        caller.requirePrimary("edi task promote");
        return new Confirmation.Promoted(promoteScout.promote(TaskReference.parse(reference), request.title(), caller.callingSession(), caller.actor()));
    }

    @PostMapping("/{reference}/drop")
    Confirmation drop(@PathVariable String reference, @RequestBody DropRequest request, Caller caller) {
        caller.requirePrimary("edi task drop");
        return new Confirmation.Dropped(dropTask.drop(TaskReference.parse(reference), request.discardingWork(), caller.callingSession(),
                caller.actor()));
    }

    @PostMapping("/{reference}/key")
    Confirmation key(@PathVariable String reference, @RequestBody KeyRequest request, Caller caller) {
        caller.requirePrimary("edi task key");
        return new Confirmation.Keyed(keyTask.key(TaskReference.parse(reference), request.workKey(), request.citation(), caller.callingSession(),
                caller.actor()));
    }

    @PostMapping("/{id}/adopt")
    @ResponseStatus(HttpStatus.CREATED)
    Confirmation adopt(@PathVariable String id, @RequestBody AdoptRequest request, Caller caller) {
        caller.requirePrimary("edi task adopt");
        return new Confirmation.Adopted(adoptForkTask.adopt(new TaskId(id), request.oldHome(), request.branchName(), request.oldCopy(),
                request.sessionToResume(), caller.actor()));
    }
}
