package io.github.ythibos.edi.web.decision;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.decision.AnswerDecision;
import io.github.ythibos.edi.application.decision.AnsweredDecision;
import io.github.ythibos.edi.application.decision.DecisionList;
import io.github.ythibos.edi.application.decision.DecisionListQuery;
import io.github.ythibos.edi.application.decision.RaiseDecision;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.web.Confirmation;
import io.github.ythibos.edi.web.access.Caller;

@RestController
class DecisionController {

    private final DecisionListQuery list;
    private final RaiseDecision raiseDecision;
    private final AnswerDecision answerDecision;

    DecisionController(DecisionListQuery list, RaiseDecision raiseDecision, AnswerDecision answerDecision) {
        this.list = list;
        this.raiseDecision = raiseDecision;
        this.answerDecision = answerDecision;
    }

    @GetMapping("/api/decisions")
    DecisionList decisions(@RequestParam(defaultValue = "false") boolean open) {
        return open ? list.open() : list.all();
    }

    @PostMapping("/api/tasks/{reference}/decision")
    @ResponseStatus(HttpStatus.CREATED)
    Confirmation raise(@PathVariable String reference, @RequestBody CardRequest card, Caller caller) {
        caller.requirePrimary("edi decision raise");
        return new Confirmation.Raised(raiseDecision.raise(TaskReference.parse(reference), card.card(), caller.actor()));
    }

    @PostMapping("/api/decisions/{reference}/answer")
    Confirmation answer(@PathVariable String reference, @RequestBody AnswerRequest request, Caller caller) {
        caller.requirePrimary("edi decision answer");
        AnsweredDecision answered = answerDecision.answer(TaskReference.parse(reference), request.givenInChat(), caller.actor());
        return new Confirmation.Answered(answered.decision(), answered.task());
    }
}
