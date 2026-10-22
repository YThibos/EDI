package io.github.ythibos.edi.web.board;

import java.nio.file.Path;
import java.util.List;

import io.github.ythibos.edi.domain.board.BoardRound;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.TaskReference;

record ArmRequest(String file, String task, String link, Integer questions, List<String> bind) {

    Path board() {
        if (file == null || file.isBlank()) {
            throw new IllegalArgumentException("Arming a board names its HTML file.");
        }
        return Path.of(file);
    }

    TaskReference taskOfTheBoard() {
        if (task == null || task.isBlank()) {
            throw new IllegalArgumentException("Arming a board names the task it belongs to.");
        }
        return TaskReference.parse(task);
    }

    BoardRound round() {
        if (questions == null) {
            throw new IllegalArgumentException("Arming a board counts the questions it puts before the captain.");
        }
        return new BoardRound(link, questions, bound());
    }

    private List<DecisionNumber> bound() {
        return bind == null ? List.of() : bind.stream().map(String::strip).filter(number -> !number.isEmpty()).map(DecisionNumber::parse).toList();
    }
}
