package io.github.ythibos.edi.web.worker;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.worker.OpenWorkerQuestions;
import io.github.ythibos.edi.application.worker.OpenWorkerQuestionsQuery;
import io.github.ythibos.edi.application.worker.PeekWorker;
import io.github.ythibos.edi.application.worker.RecordStatus;
import io.github.ythibos.edi.application.worker.RelaunchWorker;
import io.github.ythibos.edi.application.worker.SteerWorker;
import io.github.ythibos.edi.application.worker.StopWorker;
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
    private final RelaunchWorker relaunchWorker;
    private final StopWorker stopWorker;
    private final OpenWorkerQuestionsQuery questions;

    WorkerController(RecordStatus recordStatus, WorkerInbox inbox, SteerWorker steerWorker, WorkerListQuery list, PeekWorker peekWorker,
            RelaunchWorker relaunchWorker, StopWorker stopWorker, OpenWorkerQuestionsQuery questions) {
        this.recordStatus = recordStatus;
        this.inbox = inbox;
        this.steerWorker = steerWorker;
        this.list = list;
        this.peekWorker = peekWorker;
        this.relaunchWorker = relaunchWorker;
        this.stopWorker = stopWorker;
        this.questions = questions;
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

    @GetMapping("/api/workers/questions")
    OpenWorkerQuestions openQuestions(Caller caller) {
        caller.requireTheHomeToken("edi health");
        return questions.open();
    }

    @GetMapping("/api/tasks/{reference}/peek")
    PeekWorker.Peek peek(@PathVariable String reference) {
        return peekWorker.peek(TaskReference.parse(reference));
    }

    @PostMapping("/api/tasks/{reference}/relaunch")
    Confirmation relaunch(@PathVariable String reference, Caller caller) {
        caller.requirePrimary("edi worker relaunch");
        return new Confirmation.Relaunched(relaunchWorker.relaunch(TaskReference.parse(reference), caller.actor()));
    }

    @PostMapping("/api/tasks/{reference}/stop")
    Confirmation stop(@PathVariable String reference, Caller caller) {
        caller.requirePrimary("edi worker stop");
        return new Confirmation.StopRequested(stopWorker.stop(TaskReference.parse(reference), caller.actor()));
    }
}
