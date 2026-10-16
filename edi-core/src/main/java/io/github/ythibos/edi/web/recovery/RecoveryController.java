package io.github.ythibos.edi.web.recovery;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.supervision.OutageHoldList;
import io.github.ythibos.edi.application.supervision.OutageList;
import io.github.ythibos.edi.application.supervision.OutageListQuery;
import io.github.ythibos.edi.application.supervision.RecoverFromOutage;
import io.github.ythibos.edi.domain.recovery.RecoveryAction;
import io.github.ythibos.edi.web.Confirmation;
import io.github.ythibos.edi.web.access.Caller;

@RestController
class RecoveryController {

    private final OutageHoldList holdList;
    private final OutageListQuery list;
    private final RecoverFromOutage recover;

    RecoveryController(OutageHoldList holdList, OutageListQuery list, RecoverFromOutage recover) {
        this.holdList = holdList;
        this.list = list;
        this.recover = recover;
    }

    @GetMapping("/api/recovery")
    OutageList outageList() {
        return list.list();
    }

    @PostMapping("/api/recovery")
    OutageList holdTheDead(Caller caller) {
        caller.requirePrimary("edi recover");
        holdList.holdTheDead();
        return list.list();
    }

    @PostMapping("/api/recovery/{number}/{action}")
    Confirmation answer(@PathVariable int number, @PathVariable String action, @RequestBody RecoveryAnswer answer, Caller caller) {
        caller.requirePrimary("edi recover " + number + " " + action);
        return new Confirmation.Relaunched(recover.answer(number, RecoveryAction.named(action), answer.routeChange(), caller.actor()));
    }
}
