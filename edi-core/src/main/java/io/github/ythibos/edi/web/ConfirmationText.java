package io.github.ythibos.edi.web;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.web.Confirmation.Acknowledged;
import io.github.ythibos.edi.web.Confirmation.Answered;
import io.github.ythibos.edi.web.Confirmation.Deferred;
import io.github.ythibos.edi.web.Confirmation.Dispatched;
import io.github.ythibos.edi.web.Confirmation.Held;
import io.github.ythibos.edi.web.Confirmation.Noted;
import io.github.ythibos.edi.web.Confirmation.Queued;
import io.github.ythibos.edi.web.Confirmation.Raised;
import io.github.ythibos.edi.web.Confirmation.Relaunched;
import io.github.ythibos.edi.web.Confirmation.Released;
import io.github.ythibos.edi.web.Confirmation.StatusRecorded;
import io.github.ythibos.edi.web.Confirmation.Steered;
import io.github.ythibos.edi.web.Confirmation.StopRequested;
import io.github.ythibos.edi.web.text.Moments;
import io.github.ythibos.edi.web.text.TextPresenter;

@Component
class ConfirmationText implements TextPresenter<Confirmation> {

    @Override
    public Class<Confirmation> presents() {
        return Confirmation.class;
    }

    @Override
    public String text(Confirmation confirmation) {
        return sentence(confirmation) + "\n";
    }

    private static String sentence(Confirmation confirmation) {
        return switch (confirmation) {
            case Queued queued -> "Queued " + queued.task() + ".";
            case Noted noted -> "Noted on " + noted.task() + ".";
            case Held held -> "Held " + held.task().id() + " (" + held.task().hold().kind().word() + ", "
                    + Moments.until(held.task().hold().until()) + "): " + held.task().hold().reason();
            case Released released -> "Released " + released.task().id() + "; it is " + released.task().state().word() + " again.";
            case Raised raised -> raised.number().toString();
            case Answered answered -> answerRecorded(answered);
            case Dispatched dispatched -> WorkerSentences.dispatched(dispatched.launched());
            case Deferred deferred -> WorkerSentences.deferred(deferred.deferred());
            case StatusRecorded recorded -> WorkerSentences.statusRecorded(recorded.worker());
            case Acknowledged acknowledged -> "Acknowledged message " + acknowledged.message().number() + ".";
            case Steered steered -> WorkerSentences.steered(steered.steered());
            case Relaunched relaunched -> WorkerSentences.relaunched(relaunched.launched());
            case StopRequested stopped -> "Stopping " + stopped.worker().id() + ": EDI signalled its processes, and those still running in "
                    + "ten seconds are killed; edi worker relaunch " + stopped.worker().id().task() + " resumes its session.";
        };
    }

    private static String answerRecorded(Answered answered) {
        String option = answered.decision().answer().choosesAnOption() ? " (option " + answered.decision().answer().option() + ")" : "";
        String outcome = answered.task().isDone() ? " is done." : " is " + answered.task().state().word() + " again.";
        return "Recorded the answer to " + answered.decision().number() + option + "; " + answered.task().id() + outcome;
    }
}
