package io.github.ythibos.edi.testing;

import java.util.List;

import io.github.ythibos.edi.domain.board.BoardRound;
import io.github.ythibos.edi.domain.decision.DecisionNumber;

public final class BoardRounds {

    public static final String LINK = "http://127.0.0.1:4387/session/1a2b3c";

    private BoardRounds() {
    }

    public static BoardRound asking(int questions, DecisionNumber... bound) {
        return new BoardRound(LINK, questions, List.of(bound));
    }

    public static BoardRound binding(DecisionNumber... bound) {
        return asking(bound.length == 0 ? 1 : 0, bound);
    }
}
