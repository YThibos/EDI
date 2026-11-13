package io.github.ythibos.edi.domain.primary;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.ythibos.edi.domain.decision.UndeliveredAnswer;

public final class TurnEnd {

    static final int MOST_BLOCKED_STOPS = 3;
    static final String RENDER_THE_REPORT = "This turn changed state: render the turn report with edi report --turn - and paste it verbatim, then stop.";
    static final String START_THE_WAIT = "Work is in flight and no wake stream is live: start edi wait --follow alone with the Monitor tool, then stop.";

    private TurnEnd() {
    }

    public static Optional<String> blockingReason(PrimaryTurn turn, TurnCircumstances circumstances) {
        if (turn == null || turn.stopsBlocked() >= MOST_BLOCKED_STOPS) {
            return Optional.empty();
        }
        if (circumstances.leavesAnAnswerUndelivered()) {
            return Optional.of(carryTheAnswers(circumstances.undeliveredAnswers()));
        }
        if (circumstances.needsTheReport() && !turn.renderedTheReport()) {
            return Optional.of(RENDER_THE_REPORT);
        }
        if (circumstances.needsAWait()) {
            return Optional.of(START_THE_WAIT);
        }
        return Optional.empty();
    }

    private static String carryTheAnswers(List<UndeliveredAnswer> undelivered) {
        return undelivered.stream().map(TurnEnd::carryTheAnswer).collect(Collectors.joining(" ")) + " Then stop.";
    }

    private static String carryTheAnswer(UndeliveredAnswer answer) {
        return answer.decision() + " is answered and the worker of " + answer.task() + " still waits on its question " + answer.question()
                + ": carry the answer with edi steer " + answer.task() + " --resolves " + answer.question() + ".";
    }
}
