package io.github.ythibos.edi.domain.primary;

public enum TurnEnd {
    ENDS(""),
    HANDS_OVER_THE_REPORT("This turn changed state, so its turn report follows: paste it verbatim in one fenced block, then stop."),
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
        if (circumstances.needsTheReport() && !turn.renderedTheReport()) {
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

    public String instruction() {
        return instruction;
    }
}
