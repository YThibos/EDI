package io.github.ythibos.edi.application.supervision;

import java.time.Instant;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.OutageStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskReleased;
import io.github.ythibos.edi.domain.recovery.OutageEntry;
import io.github.ythibos.edi.domain.task.Task;

@Component
public class LeaveTheOutageList {

    private final OutageStore outages;
    private final TaskStore tasks;
    private final EventLog events;

    public LeaveTheOutageList(OutageStore outages, TaskStore tasks, EventLog events) {
        this.outages = outages;
        this.tasks = tasks;
        this.events = events;
    }

    public Task onceNothingIsLeftToResume(Task task, Actor actor, Instant at) {
        if (task.hasWorkToResume()) {
            return task;
        }
        return outages.holding(task.id()).map(entry -> leftBy(entry, task, actor, at)).orElse(task);
    }

    public void everyTaskWithNothingLeftToResume(Instant at) {
        outages.open().forEach(entry -> onceNothingIsLeftToResume(tasks.get(entry.task()), Actor.CORE, at));
    }

    private Task leftBy(OutageEntry entry, Task task, Actor actor, Instant at) {
        outages.close(entry);
        return releasedFromItsHold(entry, task, actor, at);
    }

    private Task releasedFromItsHold(OutageEntry entry, Task task, Actor actor, Instant at) {
        Task released = entry.withoutItsHold(task);
        if (task.isHeld() && !released.isHeld()) {
            tasks.update(released);
            events.record(new TaskReleased(released.id()), actor, at);
        }
        return released;
    }
}
