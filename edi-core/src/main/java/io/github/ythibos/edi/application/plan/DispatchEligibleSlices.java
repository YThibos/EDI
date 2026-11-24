package io.github.ythibos.edi.application.plan;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.PlanStore;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.application.dispatch.DispatchRequest;
import io.github.ythibos.edi.application.dispatch.DispatchTask;
import io.github.ythibos.edi.application.dispatch.RoomForAWorker;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent;
import io.github.ythibos.edi.domain.event.DomainEvent.SliceDispatched;
import io.github.ythibos.edi.domain.event.DomainEvent.SliceNotDispatched;
import io.github.ythibos.edi.domain.plan.Plan;
import io.github.ythibos.edi.domain.plan.PlanStandings;
import io.github.ythibos.edi.domain.plan.Slice;
import io.github.ythibos.edi.domain.plan.SliceStanding;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Service
public class DispatchEligibleSlices {

    private final PlanStore plans;
    private final PlanQuery query;
    private final DispatchStore dispatches;
    private final DispatchTask dispatchTask;
    private final RoomForAWorker room;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final Map<TaskId, String> refusalsTold = new ConcurrentHashMap<>();

    public DispatchEligibleSlices(PlanStore plans, PlanQuery query, DispatchStore dispatches, DispatchTask dispatchTask, RoomForAWorker room,
            EventLog events, Clock clock, TransactionTemplate transaction) {
        this.plans = plans;
        this.query = query;
        this.dispatches = dispatches;
        this.dispatchTask = dispatchTask;
        this.room = room;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public List<SliceDispatched> dispatchWhereThePlansHaveRoom() {
        return transaction.execute(_ -> plans.withQueuedSlices()).stream()
                .flatMap(plan -> dispatchedFrom(query.standings(plan)).stream())
                .toList();
    }

    private List<SliceDispatched> dispatchedFrom(PlanStandings standings) {
        Plan plan = standings.plan();
        List<SliceDispatched> dispatched = new ArrayList<>();
        Iterator<SliceStanding> eligible = standings.eligible().iterator();
        while (dispatched.size() < standings.freePlaces() && eligible.hasNext() && room.on(plan.heading().project())) {
            attemptedDispatchOf(plan, eligible.next().slice()).ifPresent(dispatched::add);
        }
        return dispatched;
    }

    private Optional<SliceDispatched> attemptedDispatchOf(Plan plan, Slice slice) {
        return outcomeOfDispatching(plan, slice)
                .map(outcome -> recorded(new SliceDispatched(slice.task(), plan.key(), slice.number(), workerOf(outcome))));
    }

    private Optional<DispatchOutcome> outcomeOfDispatching(Plan plan, Slice slice) {
        try {
            DispatchOutcome outcome = dispatchTask.dispatch(new TaskReference.ById(slice.task()), requestFor(slice), CallingSession.UNNAMED, Actor.CORE);
            refusalsTold.remove(slice.task());
            return Optional.of(outcome);
        } catch (RuntimeException refused) {
            tellOnce(new SliceNotDispatched(slice.task(), plan.key(), slice.number(), reasonOf(refused)));
            return Optional.empty();
        }
    }

    private DispatchRequest requestFor(Slice slice) {
        return new DispatchRequest(categoryOfAFirstDispatch(slice), "", List.of(), null);
    }

    private CategoryId categoryOfAFirstDispatch(Slice slice) {
        return transaction.execute(_ -> dispatches.of(slice.task())).isPresent() ? null : slice.category();
    }

    private static String reasonOf(RuntimeException refused) {
        return Objects.requireNonNullElse(refused.getMessage(), refused.toString());
    }

    private void tellOnce(SliceNotDispatched refusal) {
        if (!refusal.because().equals(refusalsTold.put(refusal.task(), refusal.because()))) {
            recorded(refusal);
        }
    }

    private <E extends DomainEvent> E recorded(E event) {
        transaction.executeWithoutResult(_ -> events.record(event, Actor.CORE, clock.now()));
        return event;
    }

    private static WorkerId workerOf(DispatchOutcome outcome) {
        return outcome.wasDeferred() ? null : outcome.launched().worker().id();
    }
}
