package io.github.ythibos.edi.web;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.delivery.DroppedTask;
import io.github.ythibos.edi.application.delivery.RecountedCosts;
import io.github.ythibos.edi.application.delivery.RecountedCosts.Recount;
import io.github.ythibos.edi.application.migration.AdoptedTask;
import io.github.ythibos.edi.application.task.AddedTask;
import io.github.ythibos.edi.application.tracker.KeyedTask;
import io.github.ythibos.edi.application.worker.SteerWorker;
import io.github.ythibos.edi.application.worker.StopWorker;
import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.board.BoardBinding;
import io.github.ythibos.edi.domain.delivery.TaskCost;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.tracker.TicketSettlement;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.web.Confirmation.Acknowledged;
import io.github.ythibos.edi.web.Confirmation.Adopted;
import io.github.ythibos.edi.web.Confirmation.Answered;
import io.github.ythibos.edi.web.Confirmation.BoardArmed;
import io.github.ythibos.edi.web.Confirmation.BoardRoundPosted;
import io.github.ythibos.edi.web.Confirmation.CaptainHeard;
import io.github.ythibos.edi.web.Confirmation.CleanedUp;
import io.github.ythibos.edi.web.Confirmation.CoreStopping;
import io.github.ythibos.edi.web.Confirmation.CostsRecounted;
import io.github.ythibos.edi.web.Confirmation.Deferred;
import io.github.ythibos.edi.web.Confirmation.Dispatched;
import io.github.ythibos.edi.web.Confirmation.DraftDelivered;
import io.github.ythibos.edi.web.Confirmation.Dropped;
import io.github.ythibos.edi.web.Confirmation.Held;
import io.github.ythibos.edi.web.Confirmation.Keyed;
import io.github.ythibos.edi.web.Confirmation.MergedWorkFollowedUp;
import io.github.ythibos.edi.web.Confirmation.Noted;
import io.github.ythibos.edi.web.Confirmation.PlannedFirst;
import io.github.ythibos.edi.web.Confirmation.Promoted;
import io.github.ythibos.edi.web.Confirmation.Queued;
import io.github.ythibos.edi.web.Confirmation.Raised;
import io.github.ythibos.edi.web.Confirmation.RecheckAsked;
import io.github.ythibos.edi.web.Confirmation.Relaunched;
import io.github.ythibos.edi.web.Confirmation.Released;
import io.github.ythibos.edi.web.Confirmation.StatusRecorded;
import io.github.ythibos.edi.web.Confirmation.Steered;
import io.github.ythibos.edi.web.Confirmation.StopRequested;
import io.github.ythibos.edi.web.Confirmation.WorkDiscarded;
import io.github.ythibos.edi.web.text.Moments;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.Words;

@Component
@ImportRuntimeHints(ConfirmationBinding.class)
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
            case StopRequested requested -> stopping(requested.stopped());
            case DraftDelivered delivered -> DeliverySentences.delivered(delivered.delivered());
            case MergedWorkFollowedUp followed -> DeliverySentences.followedUp(followed.work());
            case CleanedUp cleanedUp -> DeliverySentences.cleanedUp(cleanedUp.task());
            case BoardArmed armed -> boardArmed(armed.board());
            case BoardRoundPosted posted -> "Posted a new round on the research board " + posted.board().file() + " for " + posted.board().task() + ";"
                    + whatTheSitrepPutsBeforeTheCaptain(posted.board());
            case RecheckAsked asked -> "You asked for a re-check of " + asked.number() + " against reality; it stays open until it is answered.";
            case CaptainHeard heard -> captainHeard(heard);
            case Promoted promoted -> "Promoted the scout " + promoted.promoted().scout() + " to the ship " + promoted.promoted().ship().id()
                    + ", queued on the captain's word.";
            case Dropped dropped -> dropped(dropped.dropped());
            case Keyed keyed -> keyed(keyed.keyed());
            case Adopted adopted -> adopted(adopted.adopted());
            case CostsRecounted recounted -> costsRecounted(recounted.recounted());
            case WorkDiscarded discarded -> "Discarded the isolated copy and the branch of " + discarded.discarded().task()
                    + " on the captain's word" + discardedOf(discarded.discarded().discarded());
            case CoreStopping stopping -> coreStopping(stopping);
        };
    }

    private static String dropped(DroppedTask dropped) {
        String closed = dropped.closedItsTicketDecision() ? "; the drop answers " + dropped.closedDecision() + ", its ticket decision" : "";
        return "Dropped " + dropped.task().id() + " on the captain's word" + closed + discardedOf(dropped.discarded());
    }

    private static String keyed(KeyedTask keyed) {
        String closed = keyed.closedItsTicketDecision() ? "; the key answers " + keyed.closedDecision() + ", its ticket decision, and releases it" : "";
        return "Keyed " + keyed.task().id() + " on " + keyed.task().intake().workKey() + closed + ".";
    }

    private static String adopted(AdoptedTask adopted) {
        String next = adopted.resumesASession()
                ? "its next dispatch resumes session " + adopted.session() + " there"
                : "its next dispatch starts a fresh session there with its brief";
        if (!adopted.moved()) {
            return "Registered the old copy " + adopted.oldCopy() + " of " + adopted.task() + " in place as " + adopted.copy() + ", on "
                    + adopted.branch() + " at " + adopted.head() + "; a process still works there, so I launch no worker in it: adopt again once"
                    + " that process has stopped, and the copy moves under my worktrees with its uncommitted work.";
        }
        return "Took over " + adopted.task() + ": the old copy " + adopted.oldCopy() + " moved to " + adopted.copy() + " with its uncommitted work, on "
                + adopted.branch() + " at " + adopted.head() + ", and " + next + ".";
    }

    private static String costsRecounted(RecountedCosts recounted) {
        return "Recounted the pipeline's spend of " + Words.quantity(recounted.counted(), "task cost") + " counted since " + recounted.since() + "; "
                + changedOf(recounted.changed());
    }

    private static String changedOf(List<Recount> changed) {
        if (changed.isEmpty()) {
            return "none changed.";
        }
        return changed.size() + " changed: " + changed.stream().map(ConfirmationText::recount).collect(Collectors.joining("; ")) + ".";
    }

    private static String recount(Recount recount) {
        return recount.after().task() + " from " + pipelineTokensOf(recount.before()) + " to " + pipelineTokensOf(recount.after()) + " tokens";
    }

    private static String pipelineTokensOf(TaskCost cost) {
        return cost.pipelineIfKnown().map(TokenUsage::total).map(String::valueOf).orElse("unknown");
    }

    private static String captainHeard(CaptainHeard heard) {
        String concerned = heard.word().concernsTheHome() ? "for this session" : "on " + heard.word().task() + " for this session";
        return "Recorded the captain's word " + concerned + ": \"" + heard.word().verbatim() + "\"";
    }

    private static String coreStopping(CoreStopping stopping) {
        String workers = stopping.stoppedWorkers().isEmpty()
                ? ""
                : " after signalling " + stopping.stoppedWorkers().stream().map(worker -> worker.id().toString()).collect(Collectors.joining(", "));
        return "EDI core stops" + workers + ".";
    }

    private static String discardedOf(List<String> discarded) {
        return discarded.isEmpty() ? "; nothing unlanded was discarded." : "; discarded: " + String.join("; ", discarded) + ".";
    }

    private static String stopping(StopWorker.StoppedWorker stopped) {
        Worker worker = stopped.worker();
        return "Stopping " + worker.id() + ": you signalled its processes" + leftBehindOf(stopped.leftBehind()) + ", and those still running in "
                + "ten seconds are killed; edi worker relaunch " + worker.id().task() + relaunchOutcomeOf(worker) + ".";
    }

    private static String leftBehindOf(List<Long> leftBehind) {
        if (leftBehind.isEmpty()) {
            return "";
        }
        return leftBehind.size() == 1
                ? " and process " + leftBehind.getFirst() + ", which it left running in its copy"
                : " and processes " + leftBehind.stream().map(String::valueOf).collect(Collectors.joining(", ")) + ", which it left running in its copy";
    }

    private static String relaunchOutcomeOf(Worker stopped) {
        return stopped.hasAFullContext() ? " starts a fresh session with a handover, since its context is full" : " resumes its session";
    }

    private static String boardArmed(Board board) {
        String bound = board.bindings().isEmpty()
                ? "; it answers no decision"
                : "; its answers to " + board.bindings().stream().map(ConfirmationText::boundDecision).collect(Collectors.joining(", "))
                        + " are recorded as the captain's";
        return "Armed the research board " + board.file() + " for " + board.task() + bound + ", and each round reaches your inbox;"
                + whatTheSitrepPutsBeforeTheCaptain(board);
    }

    private static String boundDecision(BoardBinding binding) {
        return binding.question().isEmpty() ? binding.decision().toString() : binding.decision() + " (question " + binding.question() + ")";
    }

    private static String whatTheSitrepPutsBeforeTheCaptain(Board board) {
        return board.waitsOnTheCaptain()
                ? " the sitrep puts " + Words.quantity(board.openQuestions(), "question") + " before the captain until a round answers them."
                : " the sitrep puts its bound decisions before the captain under the board until a round answers them.";
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
        return "Recorded the answer to " + answered.decision().number() + option + "; " + answered.task().id() + outcome + ticketOf(answered)
                + carriedOf(answered);
    }

    private static String ticketOf(Answered answered) {
        return switch (answered.ticket()) {
            case TicketSettlement.Unasked _ -> "";
            case TicketSettlement.Keyed keyed -> " It books its work on " + keyed.key() + (keyed.created() ? ", the ticket you created." : ".");
            case TicketSettlement.CreatedWithoutItsFields created -> " It books its work on " + created.key()
                    + ", the ticket you created, but the tracker refused its fields: " + created.because();
            case TicketSettlement.Pending pending -> " Its ticket is not settled yet: " + pending.because() + " You try again every 30 seconds.";
            case TicketSettlement.AskedAgain asked -> " You could not carry out the answer, so you raised the decision again: " + asked.because();
            case TicketSettlement.DropChosen _ -> " It books on no ticket; the answer authorises edi task drop " + answered.task().id() + ".";
        };
    }

    private static String carriedOf(Answered answered) {
        return answered.carried().map(steered -> settlementOf(steered) + WorkerSentences.steered(steered)).orElse("");
    }

    private static String settlementOf(SteerWorker.Steered steered) {
        StatusKey resolved = steered.message().resolves();
        return resolved == null
                ? " The answer goes to its worker without resolving the question it asked after the decision was raised; that question stays open. "
                : " The answer resolves " + resolved + ", the question its worker waits on. ";
    }
}
