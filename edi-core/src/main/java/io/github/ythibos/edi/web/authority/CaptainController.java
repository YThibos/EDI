package io.github.ythibos.edi.web.authority;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.authority.CaptainsWordEvidence;
import io.github.ythibos.edi.domain.authority.CaptainMessages;
import io.github.ythibos.edi.domain.authority.DialogAnswer;
import io.github.ythibos.edi.domain.authority.TypedMessage;
import io.github.ythibos.edi.web.Confirmation;
import io.github.ythibos.edi.web.access.Caller;

@RestController
@RegisterReflectionForBinding({TypedMessage.class, DialogAnswer.class})
class CaptainController {

    private final CaptainsWordEvidence evidence;

    CaptainController(CaptainsWordEvidence evidence) {
        this.evidence = evidence;
    }

    @PostMapping("/api/captain/said")
    Confirmation said(@RequestBody SaidRequest request, Caller caller) {
        caller.requirePrimary("edi captain said");
        return new Confirmation.CaptainHeard(evidence.record(caller.callingSession(), request.citation(), request.concernedTask(), request.act(),
                caller.actor()));
    }

    @GetMapping("/api/captain/messages")
    CaptainMessages messages(Caller caller) {
        caller.requirePrimary("edi captain messages");
        return evidence.messages(caller.callingSession());
    }
}
