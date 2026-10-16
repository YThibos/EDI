package io.github.ythibos.edi.adapter.store;

import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.task.TaskId;

@Component
public class PipelineRunStore {

    private final JdbcClient store;

    public PipelineRunStore(JdbcClient store) {
        this.store = store;
    }

    public Optional<PipelineRun> latestOf(TaskId task) {
        return store.sql("""
                        select run_id, status, active_step, head_commit, merge_request from pipeline_run
                        where task_id = :task order by seen_at desc, run_id desc limit 1""")
                .param("task", task.value())
                .query((row, _) -> new PipelineRun(row.getString("run_id"), row.getString("status"), row.getString("active_step"),
                        row.getString("head_commit"), row.getString("merge_request")))
                .optional();
    }

    public void save(TaskId task, PipelineRun run, Instant at) {
        store.sql("""
                        insert into pipeline_run (run_id, task_id, status, active_step, head_commit, merge_request, seen_at, observed_at)
                        values (:run, :task, :status, :step, :head, :mergeRequest, :at, :at)
                        on conflict (run_id) do update set status = excluded.status, active_step = excluded.active_step,
                            head_commit = excluded.head_commit, merge_request = excluded.merge_request, observed_at = excluded.observed_at""")
                .param("run", run.id())
                .param("task", task.value())
                .param("status", run.status())
                .param("step", run.activeStep())
                .param("head", run.headCommit())
                .param("mergeRequest", run.mergeRequestUrl())
                .param("at", Columns.text(at))
                .update();
    }
}
