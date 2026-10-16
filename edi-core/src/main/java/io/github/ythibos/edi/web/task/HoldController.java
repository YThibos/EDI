package io.github.ythibos.edi.web.task;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.task.HoldTask;
import io.github.ythibos.edi.application.task.ReleaseTask;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.web.Confirmation;
import io.github.ythibos.edi.web.access.Caller;

@RestController
@RequestMapping("/api/tasks/{reference}")
class HoldController {

    private final HoldTask holdTask;
    private final ReleaseTask releaseTask;

    HoldController(HoldTask holdTask, ReleaseTask releaseTask) {
        this.holdTask = holdTask;
        this.releaseTask = releaseTask;
    }

    @PostMapping("/hold")
    Confirmation hold(@PathVariable String reference, @RequestBody HoldRequest request, Caller caller) {
        caller.requirePrimary("edi hold");
        return new Confirmation.Held(holdTask.hold(TaskReference.parse(reference), request.holdKind(), request.why(), request.holdUntil(), caller.actor()));
    }

    @PostMapping("/release")
    Confirmation release(@PathVariable String reference, Caller caller) {
        caller.requirePrimary("edi release");
        return new Confirmation.Released(releaseTask.release(TaskReference.parse(reference), caller.actor()));
    }
}
