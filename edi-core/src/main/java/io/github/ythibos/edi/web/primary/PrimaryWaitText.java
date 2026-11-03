package io.github.ythibos.edi.web.primary;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.primary.PrimaryWait;
import io.github.ythibos.edi.web.text.Moments;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.Words;

@Component
class PrimaryWaitText implements TextPresenter<PrimaryWait> {

    private final Moments moments;

    PrimaryWaitText(Moments moments) {
        this.moments = moments;
    }

    @Override
    public Class<PrimaryWait> presents() {
        return PrimaryWait.class;
    }

    @Override
    public String text(PrimaryWait wait) {
        return switch (wait.sighting()) {
            case FOLLOWING -> "The wake stream is live: edi wait --follow has run since " + moments.minute(wait.since()) + ".\n";
            case WAITING_ONCE -> "edi wait is live since " + moments.minute(wait.since()) + " and ends at the next wake.\n";
            case MISSING -> wait.needsAWait()
                    ? "No edi wait is live" + whileInFlight(wait) + ": start edi wait --follow alone with the Monitor tool.\n"
                    : "No edi wait is live, and no task is in flight.\n";
            case NO_PRIMARY -> "No edi wait is live" + whileInFlight(wait) + ": no session holds the primary role.\n";
        };
    }

    private static String whileInFlight(PrimaryWait wait) {
        return wait.needsAWait() ? " while " + Words.quantity(wait.tasksInFlight(), "task") + (wait.tasksInFlight() == 1 ? " is" : " are")
                + " in flight" : "";
    }
}
