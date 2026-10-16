package io.github.ythibos.edi.web.wake;

import java.time.Duration;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import io.github.ythibos.edi.application.wake.WaitForWakes;
import io.github.ythibos.edi.application.wake.WakeDelivery;
import io.github.ythibos.edi.application.wake.WakeSignal;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.web.access.Caller;

@RestController
class WaitController {

    private static final Duration PATIENCE = Duration.ofSeconds(25);

    private final WaitForWakes waits;
    private final WakeSignal signal;

    WaitController(WaitForWakes waits, WakeSignal signal) {
        this.waits = waits;
        this.signal = signal;
    }

    @PostMapping("/api/wait")
    DeferredResult<ResponseEntity<WakeDelivery>> waitForWakes(Caller caller) {
        caller.requirePrimary("edi wait");
        UUID session = caller.claudeSession()
                .orElseThrow(() -> new Refusal("edi wait runs inside the primary's Claude Code session, which names itself in CLAUDE_CODE_SESSION_ID."));
        waits.requireThePrimary(session);
        DeferredResult<ResponseEntity<WakeDelivery>> result = new DeferredResult<>(PATIENCE.toMillis(), ResponseEntity.noContent().build());
        PendingWait pending = new PendingWait(session, result);
        result.onCompletion(() -> signal.forget(pending));
        pending.run();
        return result;
    }

    private final class PendingWait implements Runnable {

        private final UUID session;
        private final DeferredResult<ResponseEntity<WakeDelivery>> result;

        PendingWait(UUID session, DeferredResult<ResponseEntity<WakeDelivery>> result) {
            this.session = session;
            this.result = result;
        }

        @Override
        public synchronized void run() {
            if (result.isSetOrExpired()) {
                return;
            }
            signal.onNextWake(this);
            try {
                waits.deliverTo(session).ifPresent(this::delivered);
            } catch (RuntimeException failed) {
                signal.forget(this);
                result.setErrorResult(failed);
            }
        }

        private void delivered(WakeDelivery delivery) {
            signal.forget(this);
            if (!result.setResult(ResponseEntity.ok(delivery))) {
                waits.withdraw(delivery);
            }
        }
    }
}
