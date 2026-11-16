package io.github.ythibos.edi.domain.primary;

import java.util.Optional;

public enum TurnEnd {
    ENDS(""),
    CARRIES_THE_ANSWERS("An answered decision has not reached the worker waiting on its question: carry each answer below, then stop."),
    STARTS_THE_WAIT("Work is in flight and no wake stream is live: start edi wait --follow alone with the Monitor tool, then stop."),
    SHOWS_THE_REPORT("");

    static final int MOST_BLOCKED_STOPS = 3;

    private final String instruction;

    TurnEnd(String instruction) {
        this.instruction = instruction;
    }

    public static TurnEnd of(PrimaryTurn turn, TurnCircumstances circumstances) {
        if (turn == null) {
            return ENDS;
        }
        return heldBy(turn, circumstances).orElseGet(() -> circumstances.needsTheReport(turn) ? SHOWS_THE_REPORT : ENDS);
    }

    public boolean holdsTheStop() {
        return this == CARRIES_THE_ANSWERS || this == STARTS_THE_WAIT;
    }

    public boolean leavesWorkPending() {
        return holdsTheStop();
    }

    public String instruction() {
        return instruction;
    }

    private static Optional<TurnEnd> heldBy(PrimaryTurn turn, TurnCircumstances circumstances) {
        if (turn.stopsBlocked() >= MOST_BLOCKED_STOPS) {
            return Optional.empty();
        }
        if (circumstances.answerUndelivered()) {
            return Optional.of(CARRIES_THE_ANSWERS);
        }
        return circumstances.needsAWait() ? Optional.of(STARTS_THE_WAIT) : Optional.empty();
    }
}
