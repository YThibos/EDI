package io.github.ythibos.edi.web.call;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.authority.CaptainsWordEvidence;
import io.github.ythibos.edi.application.call.TickACall;
import io.github.ythibos.edi.domain.call.CallKey;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.web.Confirmation;
import io.github.ythibos.edi.web.access.Caller;

@RestController
class CallController {

    private final TickACall tickACall;
    private final CaptainsWordEvidence captainsWord;

    CallController(TickACall tickACall, CaptainsWordEvidence captainsWord) {
        this.tickACall = tickACall;
        this.captainsWord = captainsWord;
    }

    @PostMapping("/api/calls/{call}/tick")
    Confirmation tick(@PathVariable String call, @RequestBody(required = false) TickRequest request, Caller caller) {
        if (caller.answeringChannel("edi tick") == AnswerChannel.CHAT) {
            captainsWord.requireCited(caller.callingSession(), (request == null ? TickRequest.UNCITED : request).citation());
        }
        return new Confirmation.Ticked(tickACall.tick(CallKey.parse(call), caller.actor()));
    }
}
