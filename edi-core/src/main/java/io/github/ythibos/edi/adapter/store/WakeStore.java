package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.wake.Wake;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Component
public class WakeStore {

    private static final String SELECT_WAKES = "select number, kind, task_id, worker_id, detail, moment, raised_at, delivered_to from wake ";

    private final JdbcClient store;

    public WakeStore(JdbcClient store) {
        this.store = store;
    }

    void enqueue(long eventSequence, Wake wake, Instant at) {
        store.sql("""
                        insert into wake (event_sequence, kind, task_id, worker_id, detail, moment, raised_at)
                        values (:event, :kind, :task, :worker, :detail, :moment, :raised)""")
                .param("event", eventSequence)
                .param("kind", wake.kind().word())
                .param("task", wake.task().value())
                .param("worker", Columns.textOf(wake.worker()))
                .param("detail", wake.detail())
                .param("moment", Columns.text(wake.moment()))
                .param("raised", Columns.text(at))
                .update();
    }

    public List<QueuedWake> unacknowledged() {
        return wakes(store.sql(SELECT_WAKES + "where acknowledged_at is null order by number"));
    }

    public List<QueuedWake> awaitingDeliveryTo(UUID session) {
        return wakes(store.sql(SELECT_WAKES + """
                where acknowledged_at is null and offered_to is null and (delivered_to is null or delivered_to <> :session)
                order by number""").param("session", session.toString()));
    }

    public void offer(List<QueuedWake> wakes, UUID stream) {
        wakes.forEach(wake -> store.sql("update wake set offered_to = :stream where number = :number")
                .param("stream", stream.toString())
                .param("number", wake.number())
                .update());
    }

    public void withdraw(List<QueuedWake> wakes) {
        wakes.forEach(wake -> store.sql("update wake set offered_to = null where number = :number")
                .param("number", wake.number())
                .update());
    }

    public void withdrawOffersTo(UUID stream) {
        store.sql("update wake set offered_to = null where offered_to = :stream").param("stream", stream.toString()).update();
    }

    public void printed(List<Long> numbers, UUID stream, UUID session, Instant at) {
        numbers.forEach(number -> {
            int delivered = store.sql("""
                            update wake set delivered_to = :session, delivered_at = :at, offered_to = null
                            where number = :number and offered_to = :stream""")
                    .param("session", session.toString())
                    .param("at", Columns.text(at))
                    .param("number", number)
                    .param("stream", stream.toString())
                    .update();
            if (delivered > 0) {
                acknowledgeWhatSupersedes(number, at);
            }
        });
    }

    public void acknowledgeActedOn(TaskId task, Instant at) {
        store.sql("update wake set acknowledged_at = :at where acknowledged_at is null and delivered_to is not null and task_id = :task")
                .param("at", Columns.text(at))
                .param("task", task.value())
                .update();
    }

    private void acknowledgeWhatSupersedes(long number, Instant at) {
        store.sql("""
                        update wake set acknowledged_at = :at
                        where acknowledged_at is null and delivered_to is not null and number < :number
                          and task_id = (select task_id from wake where number = :number)""")
                .param("at", Columns.text(at))
                .param("number", number)
                .update();
    }

    private List<QueuedWake> wakes(StatementSpec query) {
        return query.query((row, _) -> queued(row)).list();
    }

    private static QueuedWake queued(ResultSet row) throws SQLException {
        String worker = row.getString("worker_id");
        String deliveredTo = row.getString("delivered_to");
        Wake wake = new Wake(WakeKind.named(row.getString("kind")), new TaskId(row.getString("task_id")),
                worker == null ? null : WorkerId.parse(worker), row.getString("detail"), Columns.instant(row.getString("moment")));
        return new QueuedWake(row.getLong("number"), wake, Columns.instant(row.getString("raised_at")),
                deliveredTo == null ? null : UUID.fromString(deliveredTo));
    }

    public record QueuedWake(long number, Wake wake, Instant raisedAt, UUID deliveredTo) {
    }
}
