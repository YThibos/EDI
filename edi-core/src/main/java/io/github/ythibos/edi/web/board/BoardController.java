package io.github.ythibos.edi.web.board;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.board.ArmBoard;
import io.github.ythibos.edi.application.board.AnsweredRounds;
import io.github.ythibos.edi.application.board.BoardRoundsQuery;
import io.github.ythibos.edi.application.board.PostBoardRound;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.web.Confirmation;
import io.github.ythibos.edi.web.access.Caller;

@RestController
class BoardController {

    private final ArmBoard armBoard;
    private final PostBoardRound postBoardRound;
    private final BoardRoundsQuery rounds;

    BoardController(ArmBoard armBoard, PostBoardRound postBoardRound, BoardRoundsQuery rounds) {
        this.armBoard = armBoard;
        this.postBoardRound = postBoardRound;
        this.rounds = rounds;
    }

    @PostMapping("/api/boards")
    @ResponseStatus(HttpStatus.CREATED)
    Confirmation arm(@RequestBody ArmRequest request, Caller caller) {
        return new Confirmation.BoardArmed(armBoard.arm(caller.requireWorker("edi board arm"), request.taskOfTheBoard(), request.board(), request.round()));
    }

    @GetMapping("/api/tasks/{reference}/board")
    AnsweredRounds show(@PathVariable String reference, @RequestParam(required = false) Integer round, Caller caller) {
        if (!(caller instanceof Caller.Captain)) {
            caller.requirePrimary("edi board show");
        }
        return rounds.roundsOf(TaskReference.parse(reference), round);
    }

    @PostMapping("/api/boards/rounds")
    @ResponseStatus(HttpStatus.CREATED)
    Confirmation post(@RequestBody PostRequest request, Caller caller) {
        caller.requirePrimary("edi board post");
        return new Confirmation.BoardRoundPosted(postBoardRound.post(request.board(), request.link(), request.questionsAsked(), caller.actor()));
    }
}
