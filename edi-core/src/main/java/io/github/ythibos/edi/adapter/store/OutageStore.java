package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.recovery.OutageEntry;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.DeathCause;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Component
public class OutageStore {

    private static final String SELECT_ENTRIES = "select number, task_id, worker_id, cause, listed_at from outage_entry ";

    private final JdbcClient store;

    public OutageStore(JdbcClient store) {
        this.store = store;
    }

    public List<OutageEntry> open() {
        return entries(store.sql(SELECT_ENTRIES + "order by number"));
    }

    public Optional<OutageEntry> numbered(int number) {
        return entries(store.sql(SELECT_ENTRIES + "where number = :number").param("number", number)).stream().findFirst();
    }

    public Optional<OutageEntry> holding(TaskId task) {
        return entries(store.sql(SELECT_ENTRIES + "where task_id = :task").param("task", task.value())).stream().findFirst();
    }

    public void list(OutageEntry entry) {
        store.sql("insert into outage_entry (number, task_id, worker_id, cause, listed_at) values (:number, :task, :worker, :cause, :listed)")
                .param("number", entry.number())
                .param("task", entry.task().value())
                .param("worker", entry.worker().toString())
                .param("cause", entry.cause().word())
                .param("listed", Columns.text(entry.listedAt()))
                .update();
    }

    public void close(OutageEntry entry) {
        store.sql("delete from outage_entry where number = :number").param("number", entry.number()).update();
    }

    private List<OutageEntry> entries(StatementSpec query) {
        return query.query((row, _) -> entry(row)).list();
    }

    private static OutageEntry entry(ResultSet row) throws SQLException {
        return new OutageEntry(
                row.getInt("number"),
                new TaskId(row.getString("task_id")),
                WorkerId.parse(row.getString("worker_id")),
                DeathCause.named(row.getString("cause")),
                Columns.instant(row.getString("listed_at")));
    }
}
