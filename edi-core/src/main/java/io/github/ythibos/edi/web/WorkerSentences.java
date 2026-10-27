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

    static String plannedFirst(DispatchOutcome research) {
        String planned = "Research comes first: " + research.plannedShip() + " waits for its plan and then runs as implementation. ";
        return planned + (research.wasDeferred() ? deferred(research.deferred()) : dispatched(research.launched()));
    }

    static String relaunched(DispatchOutcome.Launched launched) {
        Worker worker = launched.worker();
        String how = worker.launch().kind() == LaunchKind.RESUME ? "resuming session " + worker.session() : "with a fresh session and a handover";
        return "Relaunched " + worker.id().task() + " as " + worker.id() + " (" + worker.category() + ", " + worker.route() + "), " + how
                + ", in " + launched.worktree() + ".";
    }

    static String deferred(DispatchOutcome.Deferred deferred) {
        return "Deferred " + deferred.task() + ": " + deferred.project() + " already runs " + deferred.running() + " of its "
                + deferred.capacity() + " workers, and the task starts when a place comes free.";
    }

    static String statusRecorded(Worker worker) {
        String recorded = "Recorded " + worker.lastStatus().report().state().word() + " for " + worker.id() + ".";
        return worker.doneCheck() == DoneCheck.CHECKING ? recorded + " The mechanical change is checked before the done is accepted." : recorded;
    }

    static String steered(SteerWorker.Steered steered) {
        String waiting = "Message " + steered.message().number() + " waits for " + steered.worker().id();
        return switch (steered.delivery()) {
            case TYPED_INTO_THE_IDLE_PROMPT -> waiting + "; its idle prompt was told to run edi inbox.";
            case AT_THE_NEXT_STOP -> waiting + "; it is busy, so its next stop delivers the message.";
            case WAITING_FOR_THE_PANE -> waiting + "; its pane shows no idle prompt, so you typed nothing and wait for its next stop.";
            case WAITING_FOR_A_LIVE_WORKER -> waiting + ", whose window is closed; the message waits in its inbox.";
            case WAITING_FOR_A_FRESH_SESSION -> waiting + ", whose context is full; the message waits in its inbox for the fresh session that edi worker"
                    + " relaunch starts.";
        };
    }
}
