package io.github.ythibos.edi.web.board;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.board.ArmBoard;
import io.github.ythibos.edi.web.Confirmation;
import io.github.ythibos.edi.web.access.Caller;

@RestController
class BoardController {

    private final ArmBoard armBoard;

    BoardController(ArmBoard armBoard) {
        this.armBoard = armBoard;
    }

    @PostMapping("/api/boards")
    @ResponseStatus(HttpStatus.CREATED)
    Confirmation arm(@RequestBody ArmRequest request, Caller caller) {
        return new Confirmation.BoardArmed(armBoard.arm(caller.requireWorker("edi board arm"), request.taskOfTheBoard(), request.board(), request.round()));
    }
}
