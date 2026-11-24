package io.github.ythibos.edi.adapter.store;

import java.time.Instant;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.call.CallKey;

@Component
public class CallTickStore {

    private final JdbcClient store;

    public CallTickStore(JdbcClient store) {
        this.store = store;
    }

    public void tick(CallKey call, String subject, Instant at) {
        store.sql("""
                        insert into call_tick (call, subject, ticked_at) values (:call, :subject, :at)
                        on conflict (call) do update set subject = excluded.subject, ticked_at = excluded.ticked_at""")
                .param("call", call.toString())
                .param("subject", subject)
                .param("at", Columns.text(at))
                .update();
    }
}
