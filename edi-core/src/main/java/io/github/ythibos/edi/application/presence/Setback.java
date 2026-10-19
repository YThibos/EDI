package io.github.ythibos.edi.application.presence;

import java.time.Instant;
import java.util.Set;

import io.github.ythibos.edi.adapter.store.LoggedEvent;
import io.github.ythibos.edi.domain.task.TaskId;

public record Setback(TaskId task, String title, String event, String detail, Instant at) {

    static final Set<String> EVENTS = Set.of("StatusReported", "WorkerDied", "LostInOutage", "WorkerLaunchFailed", "DeferredLaunchFailed",
            "BranchDeliveryFailed", "ChangeNotApplied", "CleanupRefused", "RecoveryFailed", "TicketUpdateRefused");
    private static final String FAILED = "failed";

    static boolean isOne(LoggedEvent event) {
        return EVENTS.contains(event.type()) && (!event.type().equals("StatusReported") || FAILED.equals(event.payload().path("state").asString("")));
    }

    static Setback of(LoggedEvent event, String title) {
        return new Setback(event.task(), title, event.type(), detailOf(event), event.at());
    }

    private static String detailOf(LoggedEvent event) {
        return switch (event.type()) {
            case "StatusReported" -> event.payload().path("text").asString("");
            case "WorkerDied", "LostInOutage" -> event.payload().path("cause").asString("");
            case "CleanupRefused" -> String.join("; ", event.payload().path("findings").valueStream().map(finding -> finding.asString("")).toList());
            default -> event.payload().path("because").asString("");
        };
    }
}
