package io.github.ythibos.edi.domain.call;

import io.github.ythibos.edi.domain.AsciiCharacters;
import io.github.ythibos.edi.domain.board.BoardId;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.task.TaskId;

public sealed interface CallKey {

    String BOARD = "board:";
    String REVIEW = "review:";
    int MOST_BOARD_DIGITS = 18;

    static CallKey parse(String text) {
        String written = text == null ? "" : text.strip();
        if (DecisionNumber.isWritten(written)) {
            return new OfADecision(DecisionNumber.parse(written));
        }
        if (written.startsWith(BOARD) && isBoardNumber(written.substring(BOARD.length()))) {
            return new OfABoard(new BoardId(Long.parseLong(written.substring(BOARD.length()))));
        }
        if (written.startsWith(REVIEW) && TaskId.isWritten(written.substring(REVIEW.length()))) {
            return new OfAReview(new TaskId(written.substring(REVIEW.length())));
        }
        throw new IllegalArgumentException("A call on the captain's list is a D number, board:<number> or review:<task>, such as D58: " + text);
    }

    private static boolean isBoardNumber(String digits) {
        return !digits.isEmpty() && digits.length() <= MOST_BOARD_DIGITS && AsciiCharacters.isDigits(digits) && Long.parseLong(digits) > 0;
    }

    record OfADecision(DecisionNumber number) implements CallKey {

        @Override
        public String toString() {
            return number.toString();
        }
    }

    record OfABoard(BoardId board) implements CallKey {

        @Override
        public String toString() {
            return BOARD + board;
        }
    }

    record OfAReview(TaskId task) implements CallKey {

        @Override
        public String toString() {
            return REVIEW + task.value();
        }
    }
}
