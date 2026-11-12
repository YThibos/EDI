package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.task.TaskId;

@Component
public class InboxStore {

    private static final String SELECT_MESSAGES = """
            select task_id, number, body, resolves, created_at, received_at, acknowledged_at, reported_unreceived_at
            from inbox_message
            """;

    private final JdbcClient store;

    public InboxStore(JdbcClient store) {
        this.store = store;
    }

    public int nextNumber(TaskId task) {
        return store.sql("select coalesce(max(number), 0) + 1 from inbox_message where task_id = :task")
                .param("task", task.value())
                .query((row, _) -> row.getInt(1))
                .single();
    }

    public void save(InboxMessage message) {
        store.sql("""
                        insert into inbox_message (task_id, number, body, resolves, created_at, received_at, acknowledged_at,
                                                   reported_unreceived_at)
                        values (:task, :number, :body, :resolves, :created, :received, :acknowledged, :unreceived)
                        on conflict (task_id, number) do update set
                            received_at = excluded.received_at, acknowledged_at = excluded.acknowledged_at,
                            reported_unreceived_at = excluded.reported_unreceived_at""")
                .param("task", message.task().value())
                .param("number", message.number())
                .param("body", message.body())
                .param("resolves", Columns.textOf(message.resolves()))
                .param("created", Columns.text(message.createdAt()))
                .param("received", Columns.text(message.receivedAt()))
                .param("acknowledged", Columns.text(message.acknowledgedAt()))
                .param("unreceived", Columns.text(message.reportedUnreceivedAt()))
                .update();
    }

    public Optional<InboxMessage> get(TaskId task, int number) {
        return messages(store.sql(SELECT_MESSAGES + "where task_id = :task and number = :number")
                .param("task", task.value())
                .param("number", number))
                .stream()
                .findFirst();
    }

    public List<InboxMessage> allOf(TaskId task) {
        return messages(store.sql(SELECT_MESSAGES + "where task_id = :task order by number").param("task", task.value()));
    }

    public List<InboxMessage> unacknowledgedOf(TaskId task) {
        return messages(store.sql(SELECT_MESSAGES + "where task_id = :task and acknowledged_at is null order by number")
                .param("task", task.value()));
    }

    public List<InboxMessage> resolvingOf(TaskId task) {
        return messages(store.sql(SELECT_MESSAGES + "where task_id = :task and resolves is not null order by number").param("task", task.value()));
    }

    public List<InboxMessage> awaitingReceipt() {
        return messages(store.sql(SELECT_MESSAGES + """
                where received_at is null and task_id in (select id from task where stage not in ('queued', 'retired', 'dropped'))
                order by created_at, task_id, number"""));
    }

    private List<InboxMessage> messages(StatementSpec query) {
        return query.query((row, _) -> message(row)).list();
    }

    private static InboxMessage message(ResultSet row) throws SQLException {
        String resolves = row.getString("resolves");
        return new InboxMessage(
                new TaskId(row.getString("task_id")),
                row.getInt("number"),
                row.getString("body"),
                resolves == null ? null : new StatusKey(resolves),
                Columns.instant(row.getString("created_at")),
                Columns.instant(row.getString("received_at")),
                Columns.instant(row.getString("acknowledged_at")),
                Columns.instant(row.getString("reported_unreceived_at")));
    }
}
