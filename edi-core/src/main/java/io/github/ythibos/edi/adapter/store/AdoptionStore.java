package io.github.ythibos.edi.adapter.store;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.task.Adoption;
import io.github.ythibos.edi.domain.task.TaskId;

@Component
public class AdoptionStore {

    private final JdbcClient store;

    public AdoptionStore(JdbcClient store) {
        this.store = store;
    }

    public void save(Adoption adoption) {
        store.sql("""
                        insert into adoption (task_id, branch, session_id, adopted_at) values (:task, :branch, :session, :adopted)
                        on conflict (task_id) do update set branch = excluded.branch, session_id = excluded.session_id,
                            adopted_at = excluded.adopted_at""")
                .param("task", adoption.task().value())
                .param("branch", adoption.branch().toString())
                .param("session", Columns.textOf(adoption.session()))
                .param("adopted", Columns.text(adoption.adoptedAt()))
                .update();
    }

    public Optional<Adoption> of(TaskId task) {
        return store.sql("select branch, session_id, adopted_at from adoption where task_id = :task")
                .param("task", task.value())
                .query((row, _) -> new Adoption(
                        task,
                        BranchName.parse(row.getString("branch")),
                        sessionOf(row.getString("session_id")),
                        Columns.instant(row.getString("adopted_at"))))
                .optional();
    }

    private static UUID sessionOf(String session) {
        return session == null ? null : UUID.fromString(session);
    }
}
