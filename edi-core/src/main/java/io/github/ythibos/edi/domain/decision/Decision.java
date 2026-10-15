package io.github.ythibos.edi.domain.decision;

import java.time.Instant;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.task.CaptainHold;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.TaskId;

public record Decision(DecisionNumber number, TaskId task, DecisionCard card, Instant raisedAt, CaptainAnswer answer) {

    public Decision {
        if (answer != null && answer.choosesAnOption() && !card.offers(answer.option())) {
            throw new IllegalArgumentException(number + " offers no option " + answer.option() + ".");
        }
    }

    public static Decision raised(DecisionNumber number, TaskId task, DecisionCard card, Instant at) {
        return new Decision(number, task, card, at, null);
    }

    public Decision reraised(DecisionCard newCard, Instant at) {
        return raised(number, task, newCard, at);
    }

    public Decision answered(CaptainAnswer captainsAnswer) {
        if (!isOpen()) {
            throw new Refusal(number + " is already answered: " + answer.verbatim());
        }
        return new Decision(number, task, card, raisedAt, captainsAnswer);
    }

    public boolean isOpen() {
        return answer == null;
    }

    public CaptainHold holdOnItsTask() {
        return new CaptainHold(HoldKind.DECISION, number + " " + card.title(), HoldUntil.captainsWord(), raisedAt);
    }
}
