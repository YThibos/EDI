package io.github.ythibos.edi.domain.decision;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CaptainWordAct;
import io.github.ythibos.edi.domain.rule.Rule;
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
        if (!isOpen()) {
            throw new Refusal(Rule.ANSWERED_ONCE, alreadyAnswered() + "; a new card on " + task + " is a new decision with its own number.");
        }
        return raised(number, task, newCard, at);
    }

    public Decision answered(CaptainAnswer captainsAnswer) {
        requireOpen();
        return new Decision(number, task, card, raisedAt, captainsAnswer);
    }

    public void requireOpen() {
        if (!isOpen()) {
            throw new Refusal(Rule.ANSWERED_ONCE, alreadyAnswered());
        }
    }

    public boolean authorises(CaptainWordAct act) {
        return answer != null && answer.choosesAnOption() && card.authorises(answer.option(), act);
    }

    public boolean isOpen() {
        return answer == null;
    }

    public boolean wasAnsweredSince(Instant at) {
        return !isOpen() && !answer.answeredAt().isBefore(at);
    }

    public String answerForItsWorker() {
        List<String> lines = new ArrayList<>(List.of("Answer to " + number + " " + card.title()));
        if (answer.choosesAnOption()) {
            lines.add("Chosen: option " + answer.option() + ", " + card.optionOf(answer.option()).text());
        }
        lines.add("The captain's words: \"" + answer.verbatim() + "\"");
        if (!answer.interpretation().isEmpty()) {
            lines.add("Understood as: " + answer.interpretation());
        }
        return String.join("\n", lines);
    }

    public CaptainHold holdOnItsTask() {
        return new CaptainHold(HoldKind.DECISION, number + " " + card.title(), HoldUntil.captainsWord(), raisedAt);
    }

    private String alreadyAnswered() {
        return number + " is already answered: \"" + answer.verbatim() + "\"";
    }
}
