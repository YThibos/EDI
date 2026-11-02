package io.github.ythibos.edi.web.wake;

import java.time.Duration;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import io.github.ythibos.edi.application.wake.StreamPoll;
import io.github.ythibos.edi.application.wake.WaitForWakes;
import io.github.ythibos.edi.application.wake.WakeDelivery;
import io.github.ythibos.edi.application.wake.WakeSignal;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.wake.WakeStream;
import io.github.ythibos.edi.web.access.Caller;

@RestController
class WaitController {

    private static final String PRINTED_WAKES = "EDI-Wakes";
    private static final Duration PATIENCE = Duration.ofSeconds(25);

    private final WaitForWakes waits;
    private final WakeSignal signal;

    WaitController(WaitForWakes waits, WakeSignal signal) {
        this.waits = waits;
        this.signal = signal;
    }

    @PostMapping("/api/wait")
    DeferredResult<ResponseEntity<WakeDelivery>> waitForWakes(@RequestBody WaitRequest request, Caller caller) {
        UUID session = thePrimarysSession(caller);
        StreamPoll poll = request.poll();
        WakeStream stream = waits.heard(session, poll);
        return poll.ending() ? endedAtOnce() : parkedUntilAWake(stream);
    }

    private UUID thePrimarysSession(Caller caller) {
        caller.requirePrimary("edi wait");
        UUID session = caller.claudeSession()
                .orElseThrow(() -> new Refusal("edi wait runs inside the primary's Claude Code session, which names itself in CLAUDE_CODE_SESSION_ID."));
        waits.requireThePrimary(session);
        return session;
    }

    private static DeferredResult<ResponseEntity<WakeDelivery>> endedAtOnce() {
        DeferredResult<ResponseEntity<WakeDelivery>> result = new DeferredResult<>();
        result.setResult(ResponseEntity.noContent().build());
        return result;
    }

    private DeferredResult<ResponseEntity<WakeDelivery>> parkedUntilAWake(WakeStream stream) {
        DeferredResult<ResponseEntity<WakeDelivery>> result = new DeferredResult<>(PATIENCE.toMillis(), ResponseEntity.noContent().build());
        PendingWait pending = new PendingWait(stream, result);
        result.onCompletion(() -> signal.forget(pending));
        pending.run();
        return result;
    }

    private final class PendingWait implements Runnable {

        private final WakeStream stream;
        private final DeferredResult<ResponseEntity<WakeDelivery>> result;

        PendingWait(WakeStream stream, DeferredResult<ResponseEntity<WakeDelivery>> result) {
            this.stream = stream;
            this.result = result;
        }

        @Override
        public synchronized void run() {
            if (result.isSetOrExpired()) {
                return;
            }
            signal.onNextWake(this);
            try {
                waits.deliverTo(stream).ifPresent(this::delivered);
            } catch (RuntimeException failed) {
                signal.forget(this);
                result.setErrorResult(failed);
            }
        }

        private void delivered(WakeDelivery delivery) {
            signal.forget(this);
            if (!result.setResult(ResponseEntity.ok().header(PRINTED_WAKES, delivery.numbers().stream().map(String::valueOf).collect(Collectors.joining(","))).body(delivery))) {
                waits.withdraw(delivery);
            }
        }
    }
}
