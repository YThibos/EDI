package io.github.ythibos.edi.application.wake;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import io.github.ythibos.edi.adapter.process.ProcessTable;
import io.github.ythibos.edi.adapter.store.WakeStore;
import io.github.ythibos.edi.adapter.store.WakeStreamStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.wake.WakeStream;

@Service
public class WakeStreams {

    private static final String THE_ONE_WAY = "The primary keeps one wake stream: the Monitor tool running edi wait --follow alone,"
            + " or, where Monitor is unavailable, edi wait alone as a background command.";

    private final WakeStreamStore streams;
    private final WakeStore wakes;
    private final ProcessTable processes;
    private final Clock clock;

    public WakeStreams(WakeStreamStore streams, WakeStore wakes, ProcessTable processes, Clock clock) {
        this.streams = streams;
        this.wakes = wakes;
        this.processes = processes;
        this.clock = clock;
    }

    public Optional<WakeStream> liveOf(UUID session) {
        return streams.open().stream().filter(stream -> isLiveFor(stream, session)).findFirst();
    }

    boolean isOpen(WakeStream stream) {
        return streams.openNamed(stream.id()).isPresent();
    }

    WakeStream admit(StreamPoll poll, UUID session) {
        refuseAnEndedStream(poll);
        Optional<WakeStream> known = streams.openNamed(poll.stream());
        WakeStream asking = known.orElseGet(() -> newStream(poll, session));
        makeWayFor(asking);
        if (known.isEmpty()) {
            streams.open(asking);
        }
        return asking;
    }

    void retire(WakeStream stream) {
        wakes.withdrawOffersTo(stream.id());
        streams.end(stream, clock.now());
    }

    private boolean isLiveFor(WakeStream stream, UUID session) {
        return stream.serves(session) && processes.isRunning(stream.process());
    }

    private void refuseAnEndedStream(StreamPoll poll) {
        if (streams.hasEnded(poll.stream())) {
            throw new Refusal(Rule.ONE_WAKE_STREAM, "This edi wait has ended and takes no more wakes. " + THE_ONE_WAY);
        }
    }

    private WakeStream newStream(StreamPoll poll, UUID session) {
        return processes.identify(poll.pid())
                .map(process -> new WakeStream(poll.stream(), session, process, poll.follows(), clock.now()))
                .orElseThrow(() -> new Refusal(Rule.ONE_WAKE_STREAM, "edi wait names process " + poll.pid() + ", which is not running."));
    }

    private void makeWayFor(WakeStream asking) {
        List<WakeStream> others = streams.open().stream().filter(other -> !other.isTheSameAs(asking)).toList();
        others.stream().filter(other -> isLiveFor(other, asking.session())).findFirst().ifPresent(live -> {
            throw refusalNaming(live);
        });
        others.forEach(this::retire);
    }

    private static Refusal refusalNaming(WakeStream live) {
        return new Refusal(Rule.ONE_WAKE_STREAM, "edi wait is refused: the primary's wake stream is already live as " + live.command()
                + " in process " + live.process().pid()
                + ", and it keeps the wakes. " + THE_ONE_WAY + " Leave the live one running.");
    }
}
