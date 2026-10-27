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
            case LIVE -> "edi wait is live: it was running when the primary's last turn ended, at " + moments.minute(wait.seenAt()) + ".\n";
            case MISSING -> "No edi wait is live" + whileInFlight(wait) + ": the primary's last turn ended at " + moments.minute(wait.seenAt())
                    + " without one.\n";
            case MISSING_SINCE_SESSION_START -> "No edi wait is live" + whileInFlight(wait) + ": the primary's session started at "
                    + moments.minute(wait.seenAt()) + " and has not started one.\n";
            case UNCHECKED_IN_A_TURN -> "The primary is in a turn since " + moments.minute(wait.seenAt())
                    + "; its stop checks that edi wait is live.\n";
            case NO_PRIMARY -> "No edi wait is live" + whileInFlight(wait) + ": no session holds the primary role.\n";
        };
    }

    private static String whileInFlight(PrimaryWait wait) {
        return wait.needsAWait() ? " while " + Words.quantity(wait.tasksInFlight(), "task") + (wait.tasksInFlight() == 1 ? " is" : " are")
                + " in flight" : "";
    }
}
