package io.github.ythibos.edi.domain.primary;

import java.util.Optional;

public final class TurnEnd {

    static final int MOST_BLOCKED_STOPS = 3;
    static final String RENDER_THE_REPORT = "This turn changed state: render the captain report with edi report and paste it verbatim, then stop.";
    static final String START_THE_WAIT = "Work is in flight and no edi wait is live: start edi wait in the background, then stop.";

    private TurnEnd() {
    }

    public static Optional<String> blockingReason(PrimaryTurn turn, TurnCircumstances circumstances) {
        if (turn == null || turn.stopsBlocked() >= MOST_BLOCKED_STOPS) {
            return Optional.empty();
        }
        if (circumstances.needsTheReport() && !turn.renderedTheReport()) {
            return Optional.of(RENDER_THE_REPORT);
        }
        if (circumstances.needsAWait()) {
            return Optional.of(START_THE_WAIT);
        }
        return Optional.empty();
    }
}
