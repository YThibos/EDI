package io.github.ythibos.edi.domain.board;

import java.time.Instant;
import java.util.Objects;

import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;

public record BoardAnswer(BoardId board, int round, DecisionNumber decision, String verbatim, OptionKey option, Instant receivedAt,
        Instant settledAt, String refusedBecause) {

    public BoardAnswer {
        Objects.requireNonNull(board, "A board answer comes from a board.");
        Objects.requireNonNull(decision, "A board answer answers a decision.");
        Objects.requireNonNull(receivedAt, "A board answer records when it arrived.");
        verbatim = Objects.requireNonNullElse(verbatim, "").strip();
        if (verbatim.isEmpty()) {
            throw new IllegalArgumentException("A board answer records the captain's own words.");
        }
    }

    public static BoardAnswer received(BoardId board, int round, DecisionNumber decision, BoardPrompt prompt, DecisionCard card,
            Instant at) {
        return new BoardAnswer(board, round, decision, prompt.verbatim(), prompt.optionChosen(card).orElse(null), at, null, null);
    }

    public BoardAnswer settled(Instant at) {
        return new BoardAnswer(board, round, decision, verbatim, option, receivedAt, at, null);
    }

    public BoardAnswer refused(String because, Instant at) {
        return new BoardAnswer(board, round, decision, verbatim, option, receivedAt, at, because);
    }

    public boolean isSettled() {
        return settledAt != null;
    }
}
