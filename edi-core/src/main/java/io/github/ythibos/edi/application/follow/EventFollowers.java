package io.github.ythibos.edi.application.follow;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.EventCursorStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.EventRecorded;
import io.github.ythibos.edi.adapter.store.LoggedEvent;
import io.github.ythibos.edi.application.dispatch.ResearchFirstPilot;
import io.github.ythibos.edi.application.ledger.EmitFleetLedger;
import io.github.ythibos.edi.application.presence.NotifyTheCaptain;
import io.github.ythibos.edi.application.tracker.FollowTickets;

@Component
public class EventFollowers implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(EventFollowers.class);
    private static final int BATCH = 100;

    private final List<Follower> followers;
    private final EventLog events;
    private final EventCursorStore cursors;
    private final TransactionTemplate transaction;
    private final ExecutorService runs = Executors.newVirtualThreadPerTaskExecutor();

    public EventFollowers(EmitFleetLedger fleetLedger, FollowTickets tickets, ResearchFirstPilot pilot, NotifyTheCaptain captain, EventLog events,
            EventCursorStore cursors, TransactionTemplate transaction) {
        this.followers = List.of(
                Follower.named(EmitFleetLedger.FOLLOWER, fleetLedger::follow),
                Follower.named(FollowTickets.FOLLOWER, tickets::follow),
                Follower.named(ResearchFirstPilot.FOLLOWER, pilot::follow),
                Follower.named(NotifyTheCaptain.FOLLOWER, captain::follow));
        this.events = events;
        this.cursors = cursors;
        this.transaction = transaction;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void eventRecorded(EventRecorded recorded) {
        followers.forEach(follower -> runs.submit(() -> caughtUp(follower)));
    }

    @Scheduled(fixedDelayString = "${edi.followers.round:PT30S}", initialDelayString = "${edi.followers.start:PT5S}")
    public void followTheLog() {
        followers.forEach(this::caughtUp);
    }

    @Override
    public void close() {
        runs.shutdownNow();
    }

    private void caughtUp(Follower follower) {
        follower.turn().lock();
        try {
            followEveryEventAfterItsCursor(follower);
        } catch (RuntimeException failed) {
            LOG.warn("The follower {} stopped and tries again in its next round: {}", follower.name(), failed.getMessage());
        } finally {
            follower.turn().unlock();
        }
    }

    private void followEveryEventAfterItsCursor(Follower follower) {
        List<LoggedEvent> unfollowed = nextBatchFor(follower);
        while (!unfollowed.isEmpty()) {
            unfollowed.forEach(event -> followed(follower, event));
            unfollowed = nextBatchFor(follower);
        }
    }

    private List<LoggedEvent> nextBatchFor(Follower follower) {
        return transaction.execute(_ -> events.after(cursors.positionOf(follower.name()), BATCH));
    }

    private void followed(Follower follower, LoggedEvent event) {
        follower.follows().accept(event);
        transaction.executeWithoutResult(_ -> cursors.moveTo(follower.name(), event.sequence()));
    }
}
