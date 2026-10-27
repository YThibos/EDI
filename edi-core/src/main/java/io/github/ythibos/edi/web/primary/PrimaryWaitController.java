package io.github.ythibos.edi.web.primary;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.primary.PrimaryWaitQuery;
import io.github.ythibos.edi.domain.primary.PrimaryWait;

@RestController
class PrimaryWaitController {

    private final PrimaryWaitQuery waits;

    PrimaryWaitController(PrimaryWaitQuery waits) {
        this.waits = waits;
    }

    @GetMapping("/api/primary/wait")
    PrimaryWait primaryWait() {
        return waits.current();
    }
}
