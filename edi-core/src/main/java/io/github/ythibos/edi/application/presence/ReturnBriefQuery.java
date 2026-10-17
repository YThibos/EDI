package io.github.ythibos.edi.application.presence;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.fleet.FleetSnapshot;
import io.github.ythibos.edi.application.fleet.FleetSnapshotQuery;
import io.github.ythibos.edi.application.fleet.LandedTask;
import io.github.ythibos.edi.domain.presence.Presence;

@Service
public class ReturnBriefQuery {

    private final FleetSnapshotQuery fleet;
    private final EventLog events;
    private final TaskStore tasks;
    private final TransactionTemplate transaction;

    public ReturnBriefQuery(FleetSnapshotQuery fleet, EventLog events, TaskStore tasks, TransactionTemplate transaction) {
        this.fleet = fleet;
        this.events = events;
        this.tasks = tasks;
        this.transaction = transaction;
    }

    public ReturnBrief since(Presence left) {
        FleetSnapshot snapshot = fleet.snapshot();
        return new ReturnBrief(left, snapshot.decisions(), landedSince(snapshot, left.since()), setbacksSince(left.since()), snapshot.ongoing());
    }

    private static List<LandedTask> landedSince(FleetSnapshot snapshot, Instant since) {
        return snapshot.landed().stream().filter(landed -> landed.task().closed() != null && !landed.task().closed().isBefore(since)).toList();
    }

    private List<Setback> setbacksSince(Instant since) {
        return transaction.execute(_ -> events.ofTypesSince(Setback.EVENTS, since).stream()
                .filter(Setback::isOne)
                .map(event -> Setback.of(event, tasks.get(event.task()).intake().title()))
                .toList());
    }
}
