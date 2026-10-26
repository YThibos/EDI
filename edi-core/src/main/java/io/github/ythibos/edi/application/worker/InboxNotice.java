package io.github.ythibos.edi.application.worker;

import java.util.Optional;

import org.springframework.stereotype.Service;

import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.worker.Keystroke;
import io.github.ythibos.edi.domain.worker.Worker;

@Service
public class InboxNotice {

    private final WorkerRuntime runtime;

    public InboxNotice(WorkerRuntime runtime) {
        this.runtime = runtime;
    }

    public SteerDelivery deliver(Worker worker, InboxMessage message) {
        if (!runtime.isRunning(worker.window())) {
            return SteerDelivery.WAITING_FOR_A_LIVE_WORKER;
        }
        if (worker.hasAFullContext()) {
            return SteerDelivery.WAITING_FOR_A_FRESH_SESSION;
        }
        if (!worker.isIdle()) {
            return SteerDelivery.AT_THE_NEXT_STOP;
        }
        return typedNotice(worker, message).orElse(SteerDelivery.WAITING_FOR_THE_PANE);
    }

    private Optional<SteerDelivery> typedNotice(Worker worker, InboxMessage message) {
        try {
            runtime.press(worker.window(), Keystroke.line(message.notice()));
            return Optional.of(SteerDelivery.TYPED_INTO_THE_IDLE_PROMPT);
        } catch (Refusal | ProgramFailure paneNotTaken) {
            return Optional.empty();
        }
    }
}
