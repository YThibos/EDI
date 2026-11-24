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

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.PlanStore;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.application.dispatch.DispatchRequest;
import io.github.ythibos.edi.application.dispatch.DispatchTask;
import io.github.ythibos.edi.application.dispatch.RoomForAWorker;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.Refusal;
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
    private final CaptainsGo captainsGo;
    private final DispatchTask dispatchTask;
    private final RoomForAWorker room;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final Map<TaskId, String> refusalsTold = new ConcurrentHashMap<>();

    public DispatchEligibleSlices(PlanStore plans, PlanQuery query, CaptainsGo captainsGo, DispatchTask dispatchTask, RoomForAWorker room,
            EventLog events, Clock clock, TransactionTemplate transaction) {
        this.plans = plans;
        this.query = query;
        this.captainsGo = captainsGo;
        this.dispatchTask = dispatchTask;
        this.room = room;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public List<SliceDispatched> dispatchWhereThePlansHaveRoom() {
        return transaction.execute(_ -> plans.withQueuedSlices()).stream()
                .map(query::standings)
                .filter(this::goesUnderTheCaptainsGo)
                .flatMap(standings -> dispatchedFrom(standings).stream())
                .toList();
    }

    private boolean goesUnderTheCaptainsGo(PlanStandings standings) {
        try {
            captainsGo.requireGivenFor(standings.plan());
            return true;
        } catch (Refusal withoutGo) {
            standings.eligible().stream().findFirst().ifPresent(first -> tellOnce(notDispatched(standings.plan(), first, withoutGo)));
            return false;
        }
    }

    private static SliceNotDispatched notDispatched(Plan plan, SliceStanding standing, RuntimeException refused) {
        return new SliceNotDispatched(standing.slice().task(), plan.key(), standing.slice().number(), reasonOf(refused));
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

    private List<SliceDispatched> dispatchedFrom(PlanStandings standings) {
        Plan plan = standings.plan();
        List<SliceDispatched> dispatched = new ArrayList<>();
        Iterator<SliceStanding> eligible = standings.eligible().iterator();
        while (dispatched.size() < standings.freePlaces() && eligible.hasNext() && room.on(plan.heading().project())) {
            attemptedDispatchOf(plan, eligible.next()).ifPresent(dispatched::add);
        }
        return dispatched;
    }

    private Optional<SliceDispatched> attemptedDispatchOf(Plan plan, SliceStanding standing) {
        Slice slice = standing.slice();
        return outcomeOfDispatching(plan, standing)
                .map(outcome -> recorded(new SliceDispatched(slice.task(), plan.key(), slice.number(), workerOf(outcome))));
    }

    private Optional<DispatchOutcome> outcomeOfDispatching(Plan plan, SliceStanding standing) {
        try {
            DispatchOutcome outcome = dispatchTask.dispatch(new TaskReference.ById(standing.slice().task()), requestFor(standing), CallingSession.UNNAMED,
                    Actor.CORE);
            refusalsTold.remove(standing.slice().task());
            return Optional.of(outcome);
        } catch (RuntimeException refused) {
            tellOnce(notDispatched(plan, standing, refused));
            return Optional.empty();
        }
    }

    private static DispatchRequest requestFor(SliceStanding standing) {
        return new DispatchRequest(categoryOfAFirstDispatch(standing), "", List.of(), null);
    }

    private static CategoryId categoryOfAFirstDispatch(SliceStanding standing) {
        return standing.dispatched() ? null : standing.slice().category();
    }

    private static WorkerId workerOf(DispatchOutcome outcome) {
        return outcome.wasDeferred() ? null : outcome.launched().worker().id();
    }
}
