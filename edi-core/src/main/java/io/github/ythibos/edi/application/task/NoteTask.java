package io.github.ythibos.edi.application.task;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.dispatch.RewriteBrief;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskNoted;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;

@Service
public class NoteTask {

    private final TaskStore tasks;
    private final EventLog events;
    private final RewriteBrief rewriteBrief;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public NoteTask(TaskStore tasks, EventLog events, RewriteBrief rewriteBrief, Clock clock, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.events = events;
        this.rewriteBrief = rewriteBrief;
        this.clock = clock;
        this.transaction = transaction;
    }

    public TaskId note(TaskReference reference, String note, Actor actor) {
        TaskId noted = transaction.execute(_ -> recorded(reference, note, actor));
        rewriteBrief.afterANoteOn(noted);
        return noted;
    }

    private TaskId recorded(TaskReference reference, String note, Actor actor) {
        Task noted = tasks.get(reference).noted(note);
        tasks.update(noted);
        events.record(new TaskNoted(noted.id(), note.strip()), actor, clock.now());
        return noted.id();
    }
}
