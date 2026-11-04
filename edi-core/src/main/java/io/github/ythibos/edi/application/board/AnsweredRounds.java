package io.github.ythibos.edi.application.board;

import java.time.Instant;
import java.util.List;

import io.github.ythibos.edi.domain.board.BoardAnswer;
import io.github.ythibos.edi.domain.board.BoardId;
import io.github.ythibos.edi.domain.task.TaskId;

public record AnsweredRounds(TaskId task, List<Round> rounds) {

    public AnsweredRounds {
        rounds = List.copyOf(rounds);
    }

    public record Round(BoardId board, String file, int number, Instant answeredAt, String prompts, List<BoardAnswer> answers) {

        public Round {
            answers = List.copyOf(answers);
        }
    }
}
