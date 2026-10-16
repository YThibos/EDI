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

    public void acknowledgeDeliveredTo(UUID session, Instant at) {
        store.sql("update wake set acknowledged_at = :at where acknowledged_at is null and delivered_to = :session")
                .param("at", Columns.text(at))
                .param("session", session.toString())
                .update();
    }

    public void deliver(List<QueuedWake> wakes, UUID session, Instant at) {
        wakes.forEach(wake -> store.sql("update wake set delivered_to = :session, delivered_at = :at where number = :number")
                .param("session", session.toString())
                .param("at", Columns.text(at))
                .param("number", wake.number())
                .update());
    }

    public void withdraw(List<QueuedWake> wakes) {
        wakes.forEach(wake -> store.sql("update wake set delivered_to = null, delivered_at = null where number = :number and acknowledged_at is null")
                .param("number", wake.number())
                .update());
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
