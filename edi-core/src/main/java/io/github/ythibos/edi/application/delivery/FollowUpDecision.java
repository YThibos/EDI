package io.github.ythibos.edi.application.delivery;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.application.decision.RaiseDecision;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.FollowUpHeldBack;
import io.github.ythibos.edi.domain.landing.WorkAfterTheMergedHead;
import io.github.ythibos.edi.domain.task.CaptainHold;
import io.github.ythibos.edi.domain.task.Task;

@Component
public class FollowUpDecision {

    private final RaiseDecision raiseDecision;
    private final DecisionStore decisions;
    private final EventLog events;

    public FollowUpDecision(RaiseDecision raiseDecision, DecisionStore decisions, EventLog events) {
        this.raiseDecision = raiseDecision;
        this.decisions = decisions;
        this.events = events;
    }

    public void askAbout(Task merged, WorkAfterTheMergedHead work, Instant now) {
        Optional<String> waitingOn = whatHolds(merged);
        if (waitingOn.isPresent()) {
            events.record(new FollowUpHeldBack(merged.id(), work.commits(), waitingOn.get()), Actor.CORE, now);
        } else {
            raiseDecision.raise(new TaskReference.ById(merged.id()), work.card(), Actor.CORE);
        }
    }

    private Optional<String> whatHolds(Task task) {
        return decisions.ofTask(task.id()).filter(Decision::isOpen).map(open -> "decision " + open.number() + " is open")
                .or(() -> Optional.ofNullable(task.hold()).map(CaptainHold::reason));
    }
}
