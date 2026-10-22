package io.github.ythibos.edi.application.presence;

import java.util.Optional;

import io.github.ythibos.edi.adapter.store.LoggedEvent;
import io.github.ythibos.edi.domain.presence.CaptainNotice;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskKind;

public final class CaptainNotices {

    private CaptainNotices() {
    }

    public static Optional<CaptainNotice> of(LoggedEvent event, Task task) {
        String title = task.intake().title();
        return Optional.ofNullable(switch (event.type()) {
            case "DecisionRaised" -> new CaptainNotice("Decision " + text(event, "number") + " waits on you", text(event, "title"));
            case "DraftRecorded" -> new CaptainNotice("Ready for your review", title + ": " + text(event, "url"));
            case "BranchDeliveredToCaptain" -> new CaptainNotice("Ready in your local copy", title + " on " + text(event, "branch"));
            case "ChangeAppliedToCaptain" -> new CaptainNotice("Applied in your local copy", title);
            case "StatusReported" -> statusNotice(event, task);
            case "WorkerDied", "LostInOutage" -> new CaptainNotice(title + " stopped", "Its worker stopped unexpectedly; the primary sees to it.");
            case "WorkerLaunchFailed", "DeferredLaunchFailed" -> new CaptainNotice(title + " did not start", "Its worker did not start.");
            case "BranchDeliveryFailed", "ChangeNotApplied" -> new CaptainNotice("Delivery to your local copy failed", title + ": "
                    + text(event, "because"));
            default -> null;
        });
    }

    public static CaptainNotice unreadableCalendar() {
        return new CaptainNotice("The calendar cannot be read",
                "Meetings added from now on do not turn your presence quiet until Outlook answers again.");
    }

    private static CaptainNotice statusNotice(LoggedEvent event, Task task) {
        String title = task.intake().title();
        return switch (text(event, "state")) {
            case "failed" -> new CaptainNotice(title + " failed", text(event, "text"));
            case "blocked" -> new CaptainNotice(title + " is blocked", text(event, "text"));
            case "done" -> task.intake().kind() == TaskKind.SCOUT ? new CaptainNotice("Findings ready", title) : null;
            default -> null;
        };
    }

    private static String text(LoggedEvent event, String field) {
        return event.payload().path(field).asString("");
    }
}
