package io.github.ythibos.edi.web.board;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.board.ArmBoard;
import io.github.ythibos.edi.application.board.PostBoardRound;
import io.github.ythibos.edi.web.Confirmation;
import io.github.ythibos.edi.web.access.Caller;

@RestController
class BoardController {

    private final ArmBoard armBoard;
    private final PostBoardRound postBoardRound;

    BoardController(ArmBoard armBoard, PostBoardRound postBoardRound) {
        this.armBoard = armBoard;
        this.postBoardRound = postBoardRound;
    }

    @PostMapping("/api/boards")
    @ResponseStatus(HttpStatus.CREATED)
    Confirmation arm(@RequestBody ArmRequest request, Caller caller) {
        return new Confirmation.BoardArmed(armBoard.arm(caller.requireWorker("edi board arm"), request.taskOfTheBoard(), request.board(), request.round()));
    }

    @PostMapping("/api/boards/rounds")
    @ResponseStatus(HttpStatus.CREATED)
    Confirmation post(@RequestBody PostRequest request, Caller caller) {
        caller.requirePrimary("edi board post");
        return new Confirmation.BoardRoundPosted(postBoardRound.post(request.board(), request.link(), request.questionsAsked(), caller.actor()));
    }
}
