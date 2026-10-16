package io.github.ythibos.edi.web.dispatch;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.brief.WrittenBriefQuery;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.application.dispatch.DispatchTask;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.web.Confirmation;
import io.github.ythibos.edi.web.access.Caller;

@RestController
@RequestMapping("/api/tasks/{reference}")
class DispatchController {

    private final DispatchTask dispatchTask;
    private final WrittenBriefQuery writtenBrief;

    DispatchController(DispatchTask dispatchTask, WrittenBriefQuery writtenBrief) {
        this.dispatchTask = dispatchTask;
        this.writtenBrief = writtenBrief;
    }

    @PostMapping("/dispatch")
    Confirmation dispatch(@PathVariable String reference, @RequestBody DispatchBody body, Caller caller) {
        caller.requirePrimary("edi dispatch");
        DispatchOutcome outcome = dispatchTask.dispatch(TaskReference.parse(reference), body.request(), caller.actor());
        return outcome.wasDeferred() ? new Confirmation.Deferred(outcome.deferred()) : new Confirmation.Dispatched(outcome.launched());
    }

    @PostMapping("/brief")
    Brief previewedBrief(@PathVariable String reference, @RequestBody DispatchBody body) {
        return new Brief(dispatchTask.previewBrief(TaskReference.parse(reference), body.request()));
    }

    @GetMapping("/brief")
    Brief writtenBrief(@PathVariable String reference) {
        return new Brief(writtenBrief.briefOf(TaskReference.parse(reference)));
    }
}
