package io.github.ythibos.edi.web.decision;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.authority.CaptainsWordEvidence;
import io.github.ythibos.edi.application.decision.AnswerDecision;
import io.github.ythibos.edi.application.decision.AnsweredDecision;
import io.github.ythibos.edi.application.decision.AskForARecheck;
import io.github.ythibos.edi.application.decision.DecisionList;
import io.github.ythibos.edi.application.decision.DecisionListQuery;
import io.github.ythibos.edi.application.decision.RaiseDecision;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.voice.CaptainVoice;
import io.github.ythibos.edi.web.Confirmation;
import io.github.ythibos.edi.web.access.Caller;

@RestController
class DecisionController {

    private final DecisionListQuery list;
    private final RaiseDecision raiseDecision;
    private final AnswerDecision answerDecision;
    private final AskForARecheck askForARecheck;
    private final CaptainsWordEvidence captainsWord;

    DecisionController(DecisionListQuery list, RaiseDecision raiseDecision, AnswerDecision answerDecision, AskForARecheck askForARecheck,
            CaptainsWordEvidence captainsWord) {
        this.list = list;
        this.raiseDecision = raiseDecision;
        this.answerDecision = answerDecision;
        this.askForARecheck = askForARecheck;
        this.captainsWord = captainsWord;
    }

    @GetMapping("/api/decisions")
    DecisionList decisions(@RequestParam(defaultValue = "false") boolean open) {
        return open ? list.open() : list.all();
    }

    @PostMapping("/api/tasks/{reference}/decision")
    @ResponseStatus(HttpStatus.CREATED)
    Confirmation raise(@PathVariable String reference, @RequestBody CardRequest card, Caller caller) {
        caller.requirePrimary("edi decision raise");
        DecisionCard raised = card.card();
        CaptainVoice.requireOutcomes("A decision card", raised.wording());
        return new Confirmation.Raised(raiseDecision.raise(TaskReference.parse(reference), raised, caller.actor()));
    }

    @PostMapping("/api/decisions/{reference}/answer")
    Confirmation answer(@PathVariable String reference, @RequestBody AnswerRequest request, Caller caller) {
        AnswerChannel channel = caller.answeringChannel("edi decision answer");
        String verbatim = channel == AnswerChannel.CHAT ? captainsWord.requireCited(caller.callingSession(), request.citation()) : request.verbatim();
        AnsweredDecision answered = answerDecision.answer(TaskReference.parse(reference), request.givenThrough(channel, verbatim), caller.actor());
        return new Confirmation.Answered(answered.decision(), answered.task(), answered.ticket(), answered.carriedToItsWorker());
    }

    @PostMapping("/api/decisions/{reference}/recheck")
    Confirmation recheck(@PathVariable String reference, @RequestBody(required = false) RecheckRequest request, Caller caller) {
        caller.requireTheCaptain("Asking for a re-check");
        String note = request == null ? "" : request.noteOrNone();
        return new Confirmation.RecheckAsked(askForARecheck.ask(TaskReference.parse(reference), note, caller.actor()).number());
    }
}
