package io.github.ythibos.edi.web;

import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.application.worker.SteerWorker;
import io.github.ythibos.edi.domain.worker.DoneCheck;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.Worker;

final class WorkerSentences {

    private WorkerSentences() {
    }

    static String dispatched(DispatchOutcome.Launched launched) {
        Worker worker = launched.worker();
        return "Dispatched " + worker.id().task() + " to " + worker.id() + " (" + worker.category() + ", " + worker.route() + ") on "
                + launched.branch() + " in " + launched.worktree() + ".";
    }

    static String relaunched(DispatchOutcome.Launched launched) {
        Worker worker = launched.worker();
        String how = worker.launch().kind() == LaunchKind.RESUME ? "resuming session " + worker.session() : "with a fresh session";
        return "Relaunched " + worker.id().task() + " as " + worker.id() + " (" + worker.category() + ", " + worker.route() + "), " + how
                + ", in " + launched.worktree() + ".";
    }

    static String deferred(DispatchOutcome.Deferred deferred) {
        return "Deferred " + deferred.task() + ": " + deferred.project() + " already runs " + deferred.running() + " of its "
                + deferred.capacity() + " workers, and the task starts when a place comes free.";
    }

    static String statusRecorded(Worker worker) {
        String recorded = "Recorded " + worker.lastStatus().report().state().word() + " for " + worker.id() + ".";
        return worker.doneCheck() == DoneCheck.CHECKING ? recorded + " EDI checks the mechanical change before it accepts the done." : recorded;
    }

    static String steered(SteerWorker.Steered steered) {
        String waiting = "Message " + steered.message().number() + " waits for " + steered.worker().id();
        return switch (steered.delivery()) {
            case TYPED_INTO_THE_IDLE_PROMPT -> waiting + "; its idle prompt was told to run edi inbox.";
            case AT_THE_NEXT_STOP -> waiting + "; it is busy, so its next stop delivers the message.";
            case WAITING_FOR_THE_PANE -> waiting + "; its pane shows no idle prompt, so EDI typed nothing and waits for its next stop.";
            case WAITING_FOR_A_LIVE_WORKER -> waiting + ", whose window is closed; the message waits in its inbox.";
        };
    }
}
