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

    public void tick(CallKey call, String request, Instant at) {
        store.sql("""
                        insert into call_tick (call, request, ticked_at) values (:call, :request, :at)
                        on conflict (call) do update set request = excluded.request, ticked_at = excluded.ticked_at""")
                .param("call", call.toString())
                .param("request", request)
                .param("at", Columns.text(at))
                .update();
    }
}
