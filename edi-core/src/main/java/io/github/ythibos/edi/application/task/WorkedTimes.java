package io.github.ythibos.edi.application.task;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.LoggedEvent;
import io.github.ythibos.edi.domain.event.DomainEvent.DecisionRaised;
import io.github.ythibos.edi.domain.event.DomainEvent.LimitResumeTyped;
import io.github.ythibos.edi.domain.event.DomainEvent.StatusReported;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerReceived;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDispatched;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskHeld;
import io.github.ythibos.edi.domain.event.DomainEvent.UsageLimitReached;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerDied;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerRelaunched;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerSessionEnded;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStoppedOnAnError;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerTurnEnded;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.WorkMark;
import io.github.ythibos.edi.domain.task.WorkedTime;

@Component
public class WorkedTimes {

    private static final Set<String> STRETCH_ENDINGS = Set.of(
            DecisionRaised.class.getSimpleName(),
            TaskHeld.class.getSimpleName(),
            WorkerSessionEnded.class.getSimpleName(),
            WorkerDied.class.getSimpleName(),
            UsageLimitReached.class.getSimpleName(),
            WorkerStoppedOnAnError.class.getSimpleName(),
            WorkerTurnEnded.class.getSimpleName());

    private static final Set<String> STRETCH_STARTS = Set.of(
            WorkerRelaunched.class.getSimpleName(),
            LimitResumeTyped.class.getSimpleName(),
            SteerReceived.class.getSimpleName());

    private final EventLog events;

    public WorkedTimes(EventLog events) {
        this.events = events;
    }

    public Optional<WorkedTime> of(TaskId task, Instant until) {
        return WorkedTime.over(workMarksOf(task), until);
    }

    public List<WorkMark> workMarksOf(TaskId task) {
        return events.ofTask(task).stream().flatMap(event -> workMarkOf(event).stream()).toList();
    }

    private static Optional<WorkMark> workMarkOf(LoggedEvent event) {
        if (event.isA(TaskDispatched.class)) {
            return Optional.of(WorkMark.dispatched(event.at()));
        }
        if (event.isA(StatusReported.class)) {
            return Optional.of(WorkMark.reported(event.at(), StatusState.named(event.text("state"))));
        }
        if (STRETCH_STARTS.contains(event.type())) {
            return Optional.of(WorkMark.working(event.at()));
        }
        return STRETCH_ENDINGS.contains(event.type()) ? Optional.of(WorkMark.stopped(event.at())) : Optional.empty();
    }
}
