package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.delivery.TaskCost;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.TokenUsage;

@Component
public class TaskCostStore {

    private final JdbcClient store;

    public TaskCostStore(JdbcClient store) {
        this.store = store;
    }

    public void save(TaskCost cost) {
        Optional<TokenUsage> pipeline = cost.pipelineIfKnown();
        store.sql("""
                        insert into task_cost (task_id, worker_input_tokens, worker_cache_read_tokens, worker_cache_write_tokens, worker_output_tokens,
                                               pipeline_input_tokens, pipeline_cache_read_tokens, pipeline_cache_write_tokens, pipeline_output_tokens,
                                               counted_at)
                        values (:task, :workerInput, :workerCacheRead, :workerCacheWrite, :workerOutput,
                                :pipelineInput, :pipelineCacheRead, :pipelineCacheWrite, :pipelineOutput, :counted)""")
                .param("task", cost.task().value())
                .param("workerInput", cost.workers().input())
                .param("workerCacheRead", cost.workers().cacheRead())
                .param("workerCacheWrite", cost.workers().cacheWrite())
                .param("workerOutput", cost.workers().output())
                .param("pipelineInput", pipeline.map(TokenUsage::input).orElse(null))
                .param("pipelineCacheRead", pipeline.map(TokenUsage::cacheRead).orElse(null))
                .param("pipelineCacheWrite", pipeline.map(TokenUsage::cacheWrite).orElse(null))
                .param("pipelineOutput", pipeline.map(TokenUsage::output).orElse(null))
                .param("counted", Columns.text(cost.countedAt()))
                .update();
    }

    public void updatePipelineSpend(TaskCost cost) {
        Optional<TokenUsage> pipeline = cost.pipelineIfKnown();
        store.sql("""
                        update task_cost
                        set pipeline_input_tokens = :pipelineInput, pipeline_cache_read_tokens = :pipelineCacheRead,
                            pipeline_cache_write_tokens = :pipelineCacheWrite, pipeline_output_tokens = :pipelineOutput
                        where task_id = :task""")
                .param("task", cost.task().value())
                .param("pipelineInput", pipeline.map(TokenUsage::input).orElse(null))
                .param("pipelineCacheRead", pipeline.map(TokenUsage::cacheRead).orElse(null))
                .param("pipelineCacheWrite", pipeline.map(TokenUsage::cacheWrite).orElse(null))
                .param("pipelineOutput", pipeline.map(TokenUsage::output).orElse(null))
                .update();
    }

    public Optional<TaskCost> of(TaskId task) {
        return store.sql("""
                        select task_id, worker_input_tokens, worker_cache_read_tokens, worker_cache_write_tokens, worker_output_tokens,
                               pipeline_input_tokens, pipeline_cache_read_tokens, pipeline_cache_write_tokens, pipeline_output_tokens, counted_at
                        from task_cost where task_id = :task""")
                .param("task", task.value())
                .query((row, _) -> costOf(row))
                .optional();
    }

    public List<TaskCost> countedSince(Instant from) {
        return store.sql("""
                        select task_id, worker_input_tokens, worker_cache_read_tokens, worker_cache_write_tokens, worker_output_tokens,
                               pipeline_input_tokens, pipeline_cache_read_tokens, pipeline_cache_write_tokens, pipeline_output_tokens, counted_at
                        from task_cost where counted_at >= :from order by counted_at, task_id""")
                .param("from", Columns.text(from))
                .query((row, _) -> costOf(row))
                .list();
    }

    private static TaskCost costOf(ResultSet row) throws SQLException {
        return new TaskCost(new TaskId(row.getString("task_id")), tokensOf(row, "worker_"),
                row.getObject("pipeline_input_tokens") == null ? null : tokensOf(row, "pipeline_"), Columns.instant(row.getString("counted_at")));
    }

    private static TokenUsage tokensOf(ResultSet row, String spender) throws SQLException {
        return new TokenUsage(row.getLong(spender + "input_tokens"), row.getLong(spender + "cache_read_tokens"),
                row.getLong(spender + "cache_write_tokens"), row.getLong(spender + "output_tokens"));
    }
}
