package io.github.ythibos.edi.adapter.store;

import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class MemoryCheckStore {

    private final JdbcClient store;

    public MemoryCheckStore(JdbcClient store) {
        this.store = store;
    }

    public Optional<CheckedMemory> latest() {
        return store.sql("select checked_at, tokens, budget from memory_check order by checked_at desc limit 1")
                .query((row, _) -> new CheckedMemory(Columns.instant(row.getString("checked_at")), row.getInt("tokens"), row.getInt("budget")))
                .optional();
    }

    public void record(CheckedMemory checked) {
        store.sql("insert into memory_check (checked_at, tokens, budget) values (:at, :tokens, :budget)")
                .param("at", Columns.text(checked.at()))
                .param("tokens", checked.tokens())
                .param("budget", checked.budget())
                .update();
    }

    public record CheckedMemory(Instant at, int tokens, int budget) {

        public boolean overBudget() {
            return tokens > budget;
        }
    }
}
