package io.github.ythibos.edi.adapter.store;

import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Component
public class RecoveryCostStore {

    private final JdbcClient store;

    public RecoveryCostStore(JdbcClient store) {
        this.store = store;
    }

    public boolean isCounted(WorkerId worker) {
        return store.sql("select exists (select 1 from recovery_cost where worker_id = :worker)")
                .param("worker", worker.toString())
                .query((row, _) -> row.getBoolean(1))
                .single();
    }

    public void count(WorkerId worker, TokenUsage tokens, Instant at) {
        store.sql("""
                        insert into recovery_cost (worker_id, input_tokens, cache_read_tokens, cache_write_tokens, output_tokens, counted_at)
                        values (:worker, :input, :cacheRead, :cacheWrite, :output, :counted)""")
                .param("worker", worker.toString())
                .param("input", tokens.input())
                .param("cacheRead", tokens.cacheRead())
                .param("cacheWrite", tokens.cacheWrite())
                .param("output", tokens.output())
                .param("counted", Columns.text(at))
                .update();
    }

    public List<CountedRecovery> all() {
        return store.sql("""
                        select worker.launch_kind, recovery_cost.input_tokens, recovery_cost.cache_read_tokens,
                               recovery_cost.cache_write_tokens, recovery_cost.output_tokens
                        from recovery_cost join worker on worker.id = recovery_cost.worker_id""")
                .query((row, _) -> new CountedRecovery(LaunchKind.named(row.getString("launch_kind")), new TokenUsage(
                        row.getLong("input_tokens"), row.getLong("cache_read_tokens"), row.getLong("cache_write_tokens"), row.getLong("output_tokens"))))
                .list();
    }

    public record CountedRecovery(LaunchKind launch, TokenUsage tokens) {
    }
}
