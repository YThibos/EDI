package io.github.ythibos.edi.adapter.store;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class EventCursorStore {

    private final JdbcClient store;

    public EventCursorStore(JdbcClient store) {
        this.store = store;
    }

    public long positionOf(String follower) {
        return store.sql("select coalesce(max(sequence), 0) from event_cursor where follower = :follower")
                .param("follower", follower)
                .query(Long.class)
                .single();
    }

    public void moveTo(String follower, long sequence) {
        store.sql("""
                        insert into event_cursor (follower, sequence) values (:follower, :sequence)
                        on conflict (follower) do update set sequence = excluded.sequence""")
                .param("follower", follower)
                .param("sequence", sequence)
                .update();
    }
}
