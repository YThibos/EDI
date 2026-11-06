package io.github.ythibos.edi.web.worker;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.github.ythibos.edi.application.worker.MirrorWorker;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.worker.Worker;

@Component
class LivePanes implements AutoCloseable {

    private static final Duration EVERY = Duration.ofSeconds(1);
    private static final Duration LIVE_FOR = Duration.ofMinutes(30);
    private static final String PANE = "pane";
    private static final String ENDED = "ended";

    private final MirrorWorker mirrors;
    private final ApplicationEventPublisher events;
    private final ScheduledExecutorService captures = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
    private final Set<LivePane> live = ConcurrentHashMap.newKeySet();

    LivePanes(MirrorWorker mirrors, ApplicationEventPublisher events) {
        this.mirrors = mirrors;
        this.events = events;
    }

    SseEmitter watch(Worker worker) {
        SseEmitter emitter = new SseEmitter(LIVE_FOR.toMillis());
        LivePane pane = new LivePane(worker, emitter);
        emitter.onCompletion(pane::stop);
        emitter.onTimeout(emitter::complete);
        emitter.onError(_ -> pane.stop());
        live.add(pane);
        pane.ticks = captures.scheduleWithFixedDelay(pane::capture, 0, EVERY.toMillis(), TimeUnit.MILLISECONDS);
        return emitter;
    }

    int watching() {
        return live.size();
    }

    @EventListener(ContextClosedEvent.class)
    public void coreStopping() {
        live.forEach(pane -> pane.emitter.complete());
    }

    @Override
    public void close() {
        captures.shutdownNow();
    }

    private final class LivePane {

        private final Worker worker;
        private final SseEmitter emitter;
        private volatile ScheduledFuture<?> ticks;
        private volatile boolean stopped;
        private List<String> shown;

        private LivePane(Worker worker, SseEmitter emitter) {
            this.worker = worker;
            this.emitter = emitter;
        }

        private void capture() {
            if (stopped) {
                stop();
                return;
            }
            try {
                show(mirrors.mirror(worker));
            } catch (Refusal ended) {
                sent(SseEmitter.event().name(ENDED).data(ended.getMessage()));
                emitter.complete();
                stop();
            }
        }

        private void show(List<String> lines) {
            if (lines.equals(shown)) {
                sent(SseEmitter.event().comment("unchanged"));
                return;
            }
            shown = lines;
            sent(SseEmitter.event().name(PANE).data(Map.of("lines", lines)));
        }

        private void sent(SseEmitter.SseEventBuilder event) {
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException gone) {
                stop();
            }
        }

        private void stop() {
            stopped = true;
            if (ticks != null) {
                ticks.cancel(false);
            }
            if (live.remove(this)) {
                events.publishEvent(new LivePaneStopped(worker.id().task()));
            }
        }
    }
}
