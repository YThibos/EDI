package io.github.ythibos.edi.web.wake;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.wake.WakeDelivery;
import io.github.ythibos.edi.domain.wake.Wake;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.web.presence.PresenceWords;
import io.github.ythibos.edi.web.text.Moments;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.Words;

@Component
class WakeDigestText implements TextPresenter<WakeDelivery> {

    private final Moments moments;
    private final PresenceWords presenceWords;
    private final Clock clock;

    WakeDigestText(Moments moments, PresenceWords presenceWords, Clock clock) {
        this.moments = moments;
        this.presenceWords = presenceWords;
        this.clock = clock;
    }

    @Override
    public Class<WakeDelivery> presents() {
        return WakeDelivery.class;
    }

    @Override
    public String text(WakeDelivery delivery) {
        List<String> lines = new ArrayList<>(List.of("EDI wake: " + delivery.wakes().size() + " waiting."));
        delivery.wakes().forEach(queued -> lines.add(queued.number() + ". " + sentenceOf(queued.wake())));
        lines.add("Fleet: " + Words.quantity(delivery.openDecisions(), "open decision") + ", " + Words.quantity(delivery.ongoing(), "task") + " in flight, "
                + delivery.outageEntries() + " on the outage hold list.");
        if (bringsARoundOfDecisions(delivery) && !delivery.presence().keepsTheChatQuiet()) {
            lines.add(roundAdvice(delivery));
        }
        if (delivery.presence().keepsTheChatQuiet()) {
            lines.add("The captain is " + presenceWords.of(delivery.presence()) + ": handle these and end the turn with no text for the captain;"
                    + " decisions, failures, blockers and ready drafts reach the captain as notifications and in their return brief.");
        }
        lines.add(closingAdvice(delivery));
        return String.join("\n", lines) + "\n";
    }

    private static boolean bringsARoundOfDecisions(WakeDelivery delivery) {
        return delivery.wakes().stream().anyMatch(queued -> queued.wake().kind() == WakeKind.DECISION_ROUND);
    }

    private static String roundAdvice(WakeDelivery delivery) {
        int open = delivery.openDecisions();
        return "A round of decisions is due: tell the captain in one line that " + Words.quantity(open, "open decision") + (open == 1 ? " waits" : " wait")
                + " for them in the console, then end the turn with the turn report; never put them to the captain one by one.";
    }

    private String sentenceOf(Wake wake) {
        String who = wake.worker() == null ? wake.task().value() : wake.worker().toString();
        return switch (wake.kind()) {
            case NEEDS_DECISION -> who + " needs a decision: " + wake.detail();
            case BLOCKED -> who + " is blocked: " + wake.detail();
            case FAILED -> who + " failed: " + wake.detail();
            case DONE -> who + " reports done: " + wake.detail();
            case LAUNCH_FAILED -> who + " sent no hook within two minutes of its launch; its pane shows " + wake.detail() + ".";
            case DEFERRED_LAUNCH_FAILED -> wake.task() + ": its deferred launch failed: " + wake.detail();
            case USAGE_LIMIT -> who + " stopped on the " + wake.detail() + resumeOf(wake);
            case CONTEXT_FULL -> who + "'s context has been full since " + moments.minute(wake.moment()) + ", so it can take no further prompt and a resume"
                    + " would stop on it again; edi worker stop " + wake.task() + ", then edi worker relaunch " + wake.task()
                    + " starts a fresh session in its copy with a handover.";
            case STOP_MISSED -> stopMissedBy(who, wake) + " was typed into its idle prompt.";
            case STOP_MISSED_NOTICE_WAITING -> stopMissedBy(who, wake) + " waits, as its pane did not take the notice; edi worker peek " + wake.task()
                    + " shows the pane.";
            case LIMIT_RESUME_HELD_BACK -> who + ": its usage limit reset, but its pane shows " + wake.detail() + ", so you typed nothing.";
            case WORKER_DIED -> who + " died: " + wake.detail() + "; edi worker relaunch " + wake.task() + " resumes its session.";
            case ON_ANOTHER_LOGIN -> who + " ran on " + wake.detail() + ", so I stopped it; edi worker relaunch " + wake.task()
                    + " launches it again on its recorded login.";
            case LOST_IN_OUTAGE -> who + " is on the outage hold list as number " + wake.detail() + "; edi recover shows the list.";
            case WORKER_STALE -> who + " has been idle without a declared wait since " + moments.minute(wake.moment()) + "; look once, then steer or mark it.";
            case BUSY_TOO_LONG -> who + " has been busy since " + moments.minute(wake.moment()) + ", past the three-hour ceiling.";
            case HEAVY_RUNS_AT_ONCE -> who + " started " + wake.detail() + "; nothing caps them, so each runs slower until they finish:"
                    + " tell the captain, whose report warns while they overlap.";
            case QUESTION_UNANSWERED -> who + " has waited " + waitedSince(wake) + " on its question " + wake.detail()
                    + "; answer it with edi steer " + wake.task() + " --resolves and its key, or I raise it again.";
            case WAIT_ENDED -> who + "'s declared wait has ended; verify what it waited for: " + wake.detail();
            case HOLD_DUE -> wake.task() + ": its hold until " + wake.detail() + " has come due.";
            case DRAFT_MERGED -> wake.task() + ": the captain merged " + wake.detail() + "; you refresh the clone and clean up.";
            case DRAFT_CLOSED -> wake.task() + ": " + wake.detail() + " was closed without a merge; its work waits for the captain's word.";
            case DELIVERY_FAILED -> wake.task() + ": its delivery to the captain's checkout failed: " + wake.detail();
            case CLEANUP_REFUSED -> wake.task() + " was not cleaned up after its merge: " + wake.detail();
            case DECISION_RAISED -> wake.task() + ": you raised " + wake.detail() + "; put it before the captain.";
            case DECISION_ROUND -> wake.task() + ": " + wake.detail() + " is in the round of decisions due now.";
            case TASK_KEYED -> wake.task() + " now books its work on " + wake.detail() + ", so it can be dispatched.";
            case TICKET_REFUSED -> wake.task() + ": the tracker refused " + wake.detail();
            case DECISION_ANSWERED -> wake.task() + ": the captain answered " + wake.detail()
                    + "; a worker waiting on its question already has the answer as a steer, so carry it only where that steer does not reach.";
            case RECHECK_ASKED -> wake.task() + ": the captain asks to re-check " + wake.detail()
                    + "; verify the latest state against reality, then close it with evidence or keep it open with a note.";
            case BOARD_ANSWERED -> wake.task() + ": the captain answered on " + wake.detail() + "; its worker has them in its inbox.";
            case BOARD_ANSWER_REFUSED -> wake.task() + ": a board answer to " + wake.detail();
        };
    }

    private String waitedSince(Wake wake) {
        return Words.quantity((int) Duration.between(wake.moment(), clock.now()).toMinutes(), "minute");
    }

    private String resumeOf(Wake wake) {
        return wake.moment() == null
                ? "; it has no reset time, so it waits for the captain."
                : "; you type one resume line after " + moments.minute(wake.moment()) + ".";
    }

    private String stopMissedBy(String who, Wake wake) {
        return who + " ended its turn at " + moments.minute(wake.moment()) + " while you could not take its stop hook, so you sent it a message to carry on:"
                + " message " + wake.detail();
    }

    private static String closingAdvice(WakeDelivery delivery) {
        return delivery.streamContinues()
                ? "Handle these; the wake stream keeps running and prints the next."
                : "Handle these, then start edi wait in the background again.";
    }
}
