package io.github.ythibos.edi.web.console;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.github.ythibos.edi.adapter.store.EventRecorded;
import io.github.ythibos.edi.adapter.store.PresenceChanged;

@RestController
class ConsoleStream implements AutoCloseable {

    private static final Duration RECONNECT_AFTER = Duration.ofMinutes(10);
    private static final Duration RETRY_AFTER_A_DROP = Duration.ofSeconds(3);
    private static final String FLEET_CHANGED = "fleet";
    private static final String PRESENCE_CHANGED = "presence";

    private final Set<SseEmitter> listening = ConcurrentHashMap.newKeySet();
    private final ExecutorService senders = Executors.newVirtualThreadPerTaskExecutor();

    @GetMapping(value = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream() throws IOException {
        SseEmitter emitter = new SseEmitter(RECONNECT_AFTER.toMillis());
        emitter.onCompletion(() -> listening.remove(emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(_ -> listening.remove(emitter));
        emitter.send(SseEmitter.event().reconnectTime(RETRY_AFTER_A_DROP.toMillis()).comment("EDI fleet stream"));
        listening.add(emitter);
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void fleetChanged(EventRecorded recorded) {
        listening.forEach(emitter -> senders.submit(() -> announceTheFleetChange(emitter, recorded.sequence())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void presenceChanged(PresenceChanged changed) {
        listening.forEach(emitter -> senders.submit(() -> announceThePresenceChange(emitter)));
    }

    @EventListener(ContextClosedEvent.class)
    public void coreStopping() {
        listening.forEach(SseEmitter::complete);
    }

    @Override
    public void close() {
        senders.shutdownNow();
    }

    private void announceTheFleetChange(SseEmitter emitter, long sequence) {
        sent(emitter, SseEmitter.event().name(FLEET_CHANGED).id(Long.toString(sequence)).data(Long.toString(sequence)));
    }

    private void announceThePresenceChange(SseEmitter emitter) {
        sent(emitter, SseEmitter.event().name(FLEET_CHANGED).data(PRESENCE_CHANGED));
    }

    private void sent(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException gone) {
            listening.remove(emitter);
        }
    }
}
