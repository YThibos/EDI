package io.github.ythibos.edi.web;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.task.AddedTask;
import io.github.ythibos.edi.domain.tracker.TicketSettlement;
import io.github.ythibos.edi.web.Confirmation.Acknowledged;
import io.github.ythibos.edi.web.Confirmation.Answered;
import io.github.ythibos.edi.web.Confirmation.CleanedUp;
import io.github.ythibos.edi.web.Confirmation.Deferred;
import io.github.ythibos.edi.web.Confirmation.Dispatched;
import io.github.ythibos.edi.web.Confirmation.DraftDelivered;
import io.github.ythibos.edi.web.Confirmation.Held;
import io.github.ythibos.edi.web.Confirmation.MergedWorkFollowedUp;
import io.github.ythibos.edi.web.Confirmation.Noted;
import io.github.ythibos.edi.web.Confirmation.PlannedFirst;
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
            case Queued queued -> queued(queued.added());
            case Noted noted -> "Noted on " + noted.task() + ".";
            case Held held -> "Held " + held.task().id() + " (" + held.task().hold().kind().word() + ", "
                    + Moments.until(held.task().hold().until()) + "): " + held.task().hold().reason();
            case Released released -> "Released " + released.task().id() + "; it is " + released.task().state().word() + " again.";
            case Raised raised -> raised.number().toString();
            case Answered answered -> answerRecorded(answered);
            case Dispatched dispatched -> WorkerSentences.dispatched(dispatched.launched());
            case PlannedFirst planned -> WorkerSentences.plannedFirst(planned.research());
            case Deferred deferred -> WorkerSentences.deferred(deferred.deferred());
            case StatusRecorded recorded -> WorkerSentences.statusRecorded(recorded.worker());
            case Acknowledged acknowledged -> "Acknowledged message " + acknowledged.message().number() + ".";
            case Steered steered -> WorkerSentences.steered(steered.steered());
            case Relaunched relaunched -> WorkerSentences.relaunched(relaunched.launched());
            case StopRequested stopped -> "Stopping " + stopped.worker().id() + ": EDI signalled its processes, and those still running in "
                    + "ten seconds are killed; edi worker relaunch " + stopped.worker().id().task() + " resumes its session.";
            case DraftDelivered delivered -> DeliverySentences.delivered(delivered.delivered());
            case MergedWorkFollowedUp followed -> DeliverySentences.followedUp(followed.work());
            case CleanedUp cleanedUp -> DeliverySentences.cleanedUp(cleanedUp.task());
        };
    }

    private static String queued(AddedTask added) {
        if (added.awaitsItsTicket()) {
            return "Queued " + added.id() + "; " + added.ticketDecision() + " asks which ticket it books on, and it waits for that answer.";
        }
        if (added.ticket() == null) {
            return "Queued " + added.id() + ".";
        }
        String summary = added.ticket().isDescribed() ? ": " + added.ticket().summary() : "";
        return "Queued " + added.id() + " on " + added.ticket().key() + summary + ".";
    }

    private static String answerRecorded(Answered answered) {
        String option = answered.decision().answer().choosesAnOption() ? " (option " + answered.decision().answer().option() + ")" : "";
        String outcome = answered.task().isDone() ? " is done." : " is " + answered.task().state().word() + " again.";
        return "Recorded the answer to " + answered.decision().number() + option + "; " + answered.task().id() + outcome + ticketOf(answered.ticket());
    }

    private static String ticketOf(TicketSettlement ticket) {
        return switch (ticket) {
            case TicketSettlement.Unasked _ -> "";
            case TicketSettlement.Keyed keyed -> " It books its work on " + keyed.key() + (keyed.created() ? ", the ticket EDI created." : ".");
            case TicketSettlement.CreatedWithoutItsFields created -> " It books its work on " + created.key()
                    + ", the ticket EDI created, but the tracker refused its fields: " + created.because();
            case TicketSettlement.Pending pending -> " Its ticket is not settled yet: " + pending.because() + " EDI tries again every 30 seconds.";
            case TicketSettlement.AskedAgain asked -> " EDI could not carry out the answer, so it raised the decision again: " + asked.because();
        };
    }
}
