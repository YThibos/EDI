package io.github.ythibos.edi.application.supervision;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.NoticeStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.HoldDateArrived;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskState;

@Service
public class NoticeDueHolds {

    private final TaskStore tasks;
    private final NoticeStore notices;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public NoticeDueHolds(TaskStore tasks, NoticeStore notices, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.notices = notices;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public List<TaskId> notice() {
        return transaction.execute(_ -> {
            Instant now = clock.now();
            LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
            List<DueHold> due = Stream.concat(tasks.inState(TaskState.QUEUED).stream(), tasks.inState(TaskState.IN_FLIGHT).stream())
                    .flatMap(task -> dueBy(task, today).stream())
                    .filter(hold -> !notices.wasNoticed(hold.task().id(), hold.task().hold()))
                    .toList();
            due.forEach(hold -> noticed(hold, now));
            return due.stream().map(hold -> hold.task().id()).toList();
        });
    }

    private static Optional<DueHold> dueBy(Task task, LocalDate today) {
        if (task.isHeld() && task.hold().until() instanceof HoldUntil.Day day && !day.date().isAfter(today)) {
            return Optional.of(new DueHold(task, day.date()));
        }
        return Optional.empty();
    }

    private void noticed(DueHold hold, Instant now) {
        notices.notice(hold.task().id(), hold.task().hold(), now);
        events.record(new HoldDateArrived(hold.task().id(), hold.day()), Actor.CORE, now);
    }

    private record DueHold(Task task, LocalDate day) {
    }
}
