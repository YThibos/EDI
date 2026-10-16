package io.github.ythibos.edi.web.worker;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.worker.PeekWorker;
import io.github.ythibos.edi.application.worker.RecordStatus;
import io.github.ythibos.edi.application.worker.SteerWorker;
import io.github.ythibos.edi.application.worker.WorkerInbox;
import io.github.ythibos.edi.application.worker.WorkerList;
import io.github.ythibos.edi.application.worker.WorkerListQuery;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.web.Confirmation;
import io.github.ythibos.edi.web.access.Caller;

@RestController
class WorkerController {

    private final RecordStatus recordStatus;
    private final WorkerInbox inbox;
    private final SteerWorker steerWorker;
    private final WorkerListQuery list;
    private final PeekWorker peekWorker;

    WorkerController(RecordStatus recordStatus, WorkerInbox inbox, SteerWorker steerWorker, WorkerListQuery list, PeekWorker peekWorker) {
        this.recordStatus = recordStatus;
        this.inbox = inbox;
        this.steerWorker = steerWorker;
        this.list = list;
        this.peekWorker = peekWorker;
    }

    @PostMapping("/api/status")
    Confirmation status(@RequestBody StatusBody body, Caller caller) {
        WorkerId worker = caller.requireWorker("edi status");
        return new Confirmation.StatusRecorded(recordStatus.report(worker, body.statusState(), body.statusKey(), body.untilInstant(), body.text()));
    }

    @GetMapping("/api/inbox")
    InboxMessages inbox(Caller caller) {
        return new InboxMessages(inbox.read(caller.requireWorker("edi inbox")));
    }

    @PostMapping("/api/inbox/{number}/ack")
    Confirmation acknowledge(@PathVariable int number, Caller caller) {
        return new Confirmation.Acknowledged(inbox.acknowledge(caller.requireWorker("edi inbox ack"), number));
    }

    @PostMapping("/api/tasks/{reference}/steer")
    Confirmation steer(@PathVariable String reference, @RequestBody SteerBody body, Caller caller) {
        caller.requirePrimary("edi steer");
        return new Confirmation.Steered(steerWorker.steer(TaskReference.parse(reference), body.message(), body.resolvedKey(), caller.actor()));
    }

    @GetMapping("/api/workers")
    WorkerList workers() {
        return list.serving();
    }

    @GetMapping("/api/tasks/{reference}/peek")
    PeekWorker.Peek peek(@PathVariable String reference) {
        return peekWorker.peek(TaskReference.parse(reference));
    }
}
