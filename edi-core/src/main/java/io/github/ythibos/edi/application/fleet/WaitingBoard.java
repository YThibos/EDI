package io.github.ythibos.edi.application.fleet;

import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.task.Task;

public record WaitingBoard(Board board, Task task) {
}
