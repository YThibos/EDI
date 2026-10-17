package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.delivery.PipelineReview;
import io.github.ythibos.edi.domain.task.TaskId;

@Component
public class PipelineReviewStore {

    private final JdbcClient store;

    public PipelineReviewStore(JdbcClient store) {
        this.store = store;
    }

    public void save(TaskId task, PipelineReview review, Instant countedAt) {
        store.sql("""
                        insert into task_review (task_id, rounds, findings, counted_at) values (:task, :rounds, :findings, :counted)
                        on conflict (task_id) do update set rounds = excluded.rounds, findings = excluded.findings, counted_at = excluded.counted_at""")
                .param("task", task.value())
                .param("rounds", review.rounds())
                .param("findings", review.findings())
                .param("counted", Columns.text(countedAt))
                .update();
    }

    public Optional<PipelineReview> of(TaskId task) {
        return store.sql("select rounds, findings from task_review where task_id = :task")
                .param("task", task.value())
                .query((row, _) -> reviewOf(row))
                .optional();
    }

    private static PipelineReview reviewOf(ResultSet row) throws SQLException {
        return new PipelineReview(countIn(row, "rounds"), countIn(row, "findings"));
    }

    private static Integer countIn(ResultSet row, String column) throws SQLException {
        int count = row.getInt(column);
        return row.wasNull() ? null : count;
    }
}
