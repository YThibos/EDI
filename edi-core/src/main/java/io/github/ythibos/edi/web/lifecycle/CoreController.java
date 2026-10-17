package io.github.ythibos.edi.web.lifecycle;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.CoreExit;
import io.github.ythibos.edi.application.lifecycle.StopTheCore;
import io.github.ythibos.edi.application.primary.PrepareThePrimary;
import io.github.ythibos.edi.application.primary.PrimaryLaunch;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.web.Confirmation;
import io.github.ythibos.edi.web.access.Caller;

@RestController
class CoreController {

    private final PrepareThePrimary primary;
    private final StopTheCore stopTheCore;
    private final CoreExit exit;

    CoreController(PrepareThePrimary primary, StopTheCore stopTheCore, CoreExit exit) {
        this.primary = primary;
        this.stopTheCore = stopTheCore;
        this.exit = exit;
    }

    @PostMapping("/api/primary/launch")
    PrimaryLaunch launchThePrimary(Caller caller) {
        caller.requireTheHomeToken("edi up");
        return primary.prepare();
    }

    @PostMapping("/api/core/stop")
    Confirmation stop(@RequestBody StopRequest request, Caller caller) {
        caller.requirePrimary("edi down");
        List<Worker> stopped = stopTheCore.stopFor(request.stop(), caller.callingSession(), caller.actor());
        exit.afterTheAnswer();
        return new Confirmation.CoreStopping(stopped);
    }
}
