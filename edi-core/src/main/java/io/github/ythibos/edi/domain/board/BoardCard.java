package io.github.ythibos.edi.domain.board;

import java.util.Objects;

import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionNumber;

public record BoardCard(String question, DecisionCard card) {

    public BoardCard {
        question = Objects.requireNonNullElse(question, "").strip();
        Objects.requireNonNull(card, "A board puts a decision card before the captain.");
        if (question.isEmpty()) {
            throw new IllegalArgumentException("A decision card on a board names the board question that answers it: the question its answer's data "
                    + "names, which is the form's data-lavish-question.");
        }
    }

    public BoardBinding raisedAs(DecisionNumber decision) {
        return new BoardBinding(decision, question);
    }
}
