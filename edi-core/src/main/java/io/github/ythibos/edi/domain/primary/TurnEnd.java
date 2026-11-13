package io.github.ythibos.edi.domain.primary;

public enum TurnEnd {
    ENDS(""),
    CARRIES_THE_ANSWERS("An answered decision has not reached the worker waiting on its question: carry each answer below, then stop."),
    HANDS_OVER_THE_REPORT("This turn's report follows: paste it verbatim in one fenced block, then stop."),
    STARTS_THE_WAIT("Work is in flight and no wake stream is live: start edi wait --follow alone with the Monitor tool, then stop.");

    static final int MOST_BLOCKED_STOPS = 3;

    private final String instruction;

    TurnEnd(String instruction) {
        this.instruction = instruction;
    }

    public static TurnEnd of(PrimaryTurn turn, TurnCircumstances circumstances) {
        if (turn == null || turn.stopsBlocked() >= MOST_BLOCKED_STOPS) {
            return ENDS;
        }
        if (circumstances.answerUndelivered()) {
            return CARRIES_THE_ANSWERS;
        }
        if (circumstances.needsTheReport(turn)) {
            return HANDS_OVER_THE_REPORT;
        }
        if (circumstances.needsAWait()) {
            return STARTS_THE_WAIT;
        }
        return ENDS;
    }

    public boolean holdsTheStop() {
        return this != ENDS;
    }

    public boolean leavesWorkPending() {
        return this == CARRIES_THE_ANSWERS || this == STARTS_THE_WAIT;
    }

    public String instruction() {
        return instruction;
    }
}
