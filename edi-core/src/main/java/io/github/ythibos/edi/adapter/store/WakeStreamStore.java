package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.primary.SessionProcess;
import io.github.ythibos.edi.domain.wake.WakeStream;

@Component
public class WakeStreamStore {

    private static final String SELECT_STREAMS = "select id, session_id, pid, start_tick, follows, started_at from wake_stream ";

    private final JdbcClient store;

    public WakeStreamStore(JdbcClient store) {
        this.store = store;
    }

    public void open(WakeStream stream) {
        store.sql("""
                        insert into wake_stream (id, session_id, pid, start_tick, follows, started_at)
                        values (:id, :session, :pid, :tick, :follows, :started)""")
                .param("id", stream.id().toString())
                .param("session", stream.session().toString())
                .param("pid", stream.process().pid())
                .param("tick", stream.process().startTick())
                .param("follows", stream.follows() ? 1 : 0)
                .param("started", Columns.text(stream.startedAt()))
                .update();
    }

    public List<WakeStream> open() {
        return store.sql(SELECT_STREAMS + "where ended_at is null order by started_at").query((row, _) -> stream(row)).list();
    }

    public Optional<WakeStream> openNamed(UUID id) {
        return store.sql(SELECT_STREAMS + "where ended_at is null and id = :id").param("id", id.toString()).query((row, _) -> stream(row)).optional();
    }

    public boolean hasEnded(UUID id) {
        return store.sql("select exists (select 1 from wake_stream where id = :id and ended_at is not null)")
                .param("id", id.toString())
                .query((row, _) -> row.getBoolean(1))
                .single();
    }

    public void end(WakeStream stream, Instant at) {
        store.sql("update wake_stream set ended_at = :at where id = :id and ended_at is null")
                .param("at", Columns.text(at))
                .param("id", stream.id().toString())
                .update();
    }

    private static WakeStream stream(ResultSet row) throws SQLException {
        long tick = row.getLong("start_tick");
        Long startTick = row.wasNull() ? null : tick;
        return new WakeStream(UUID.fromString(row.getString("id")), UUID.fromString(row.getString("session_id")),
                new SessionProcess(row.getLong("pid"), startTick), row.getInt("follows") == 1, Columns.instant(row.getString("started_at")));
    }
}
