package io.github.ythibos.edi.adapter.store;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.WorkKey;

@Component
public class WorkKeySequenceStore {

    private final JdbcClient store;

    public WorkKeySequenceStore(JdbcClient store) {
        this.store = store;
    }

    public WorkKey next(ProjectKey prefix) {
        WorkKey next = new WorkKey(prefix, Math.max(lastIssued(prefix), highestOnATask(prefix)) + 1);
        store.sql("""
                        insert into work_key_sequence (prefix, last_number) values (:prefix, :number)
                        on conflict (prefix) do update set last_number = excluded.last_number""")
                .param("prefix", prefix.value())
                .param("number", next.number())
                .update();
        return next;
    }

    private int lastIssued(ProjectKey prefix) {
        return store.sql("select coalesce(max(last_number), 0) from work_key_sequence where prefix = :prefix")
                .param("prefix", prefix.value())
                .query((row, _) -> row.getInt(1))
                .single();
    }

    private int highestOnATask(ProjectKey prefix) {
        return store.sql("select coalesce(max(cast(substr(work_key, :digits) as integer)), 0) from task where work_key like :pattern")
                .param("digits", prefix.value().length() + 2)
                .param("pattern", prefix.value() + "-%")
                .query((row, _) -> row.getInt(1))
                .single();
    }
}
