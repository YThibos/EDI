package io.github.ythibos.edi.web.wake;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.wake.WakeDelivery;
import io.github.ythibos.edi.domain.wake.Wake;
import io.github.ythibos.edi.web.text.Moments;
import io.github.ythibos.edi.web.text.TextPresenter;

@Component
class WakeDigestText implements TextPresenter<WakeDelivery> {

    @Override
    public Class<WakeDelivery> presents() {
        return WakeDelivery.class;
    }

    @Override
    public String text(WakeDelivery delivery) {
        List<String> lines = new ArrayList<>(List.of("EDI wake: " + delivery.wakes().size() + " waiting."));
        delivery.wakes().forEach(queued -> lines.add(queued.number() + ". " + sentenceOf(queued.wake())));
        lines.add("Fleet: " + delivery.openDecisions() + " open decisions, " + delivery.ongoing() + " tasks in flight, "
                + delivery.outageEntries() + " on the outage hold list.");
        lines.add("Handle these, then start edi wait in the background again.");
        return String.join("\n", lines) + "\n";
    }

    private static String sentenceOf(Wake wake) {
        String who = wake.worker() == null ? wake.task().value() : wake.worker().toString();
        return switch (wake.kind()) {
            case NEEDS_DECISION -> who + " needs a decision: " + wake.detail();
            case BLOCKED -> who + " is blocked: " + wake.detail();
            case FAILED -> who + " failed: " + wake.detail();
            case DONE -> who + " reports done: " + wake.detail();
            case LAUNCH_FAILED -> who + " sent no hook within two minutes of its launch; its pane shows " + wake.detail() + ".";
            case DEFERRED_LAUNCH_FAILED -> wake.task() + ": its deferred launch failed: " + wake.detail();
            case USAGE_LIMIT -> who + " stopped on the " + wake.detail() + resumeOf(wake);
            case LIMIT_RESUME_HELD_BACK -> who + ": its usage limit reset, but its pane shows " + wake.detail() + ", so EDI typed nothing.";
            case WORKER_DIED -> who + " died: " + wake.detail() + "; edi worker relaunch " + wake.task() + " resumes its session.";
            case LOST_IN_OUTAGE -> who + " is on the outage hold list as number " + wake.detail() + "; edi recover shows the list.";
            case WORKER_STALE -> who + " has been idle without a declared wait since " + Moments.minute(wake.moment()) + "; look once, then steer or mark it.";
            case BUSY_TOO_LONG -> who + " has been busy since " + Moments.minute(wake.moment()) + ", past the three-hour ceiling.";
            case WAIT_ENDED -> who + "'s declared wait has ended; verify what it waited for: " + wake.detail();
            case HOLD_DUE -> wake.task() + ": its hold until " + wake.detail() + " has come due.";
            case DRAFT_MERGED -> wake.task() + ": the captain merged " + wake.detail() + "; EDI refreshes the clone and cleans up.";
            case DRAFT_CLOSED -> wake.task() + ": " + wake.detail() + " was closed without a merge; its work waits for the captain's word.";
            case DELIVERY_FAILED -> wake.task() + ": its delivery to the captain's checkout failed: " + wake.detail();
            case CLEANUP_REFUSED -> wake.task() + " was not cleaned up after its merge: " + wake.detail();
        };
    }

    private static String resumeOf(Wake wake) {
        return wake.moment() == null
                ? "; it has no reset time, so it waits for the captain."
                : "; EDI types one resume line after " + Moments.minute(wake.moment()) + ".";
    }
}
