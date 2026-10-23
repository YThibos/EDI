package io.github.ythibos.edi.domain.board;

import java.util.Objects;

import io.github.ythibos.edi.domain.decision.DecisionNumber;

public record BoardBinding(DecisionNumber decision, String question) {

    public BoardBinding {
        Objects.requireNonNull(decision, "A board binds a decision.");
        question = Objects.requireNonNullElse(question, "").strip();
    }

    public static BoardBinding byNumber(DecisionNumber decision) {
        return new BoardBinding(decision, "");
    }

    public boolean isAnsweredBy(BoardPrompt prompt) {
        return prompt.decisionNamed().filter(decision::equals).isPresent() || isTheQuestionOf(prompt);
    }

    private boolean isTheQuestionOf(BoardPrompt prompt) {
        return !question.isEmpty() && prompt.questionNamed().filter(question::equals).isPresent();
    }
}
