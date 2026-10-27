package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.primary.PrimarySession;
import io.github.ythibos.edi.domain.primary.PrimaryTurn;
import io.github.ythibos.edi.domain.primary.SessionProcess;
import io.github.ythibos.edi.domain.worker.UsageLimit;

@Component
public class PrimarySessionStore {

    private static final String SELECT_SESSIONS = """
            select session_id, started_at, claude_pid, claude_start_tick, claimed_at, turn_started_at, turn_event_mark, reported_at,
                   stops_blocked, typed_at, notification, notified_at, limit_name, limit_resets_at, limit_reached_at, ended_at,
                   sitrep_opened_at, turn_ended_at, turn_ended_waiting
            from primary_session
            """;

    private final JdbcClient store;

    public PrimarySessionStore(JdbcClient store) {
        this.store = store;
    }

    public void save(PrimarySession session) {
        SessionProcess process = session.process();
        PrimaryTurn turn = session.turn();
        UsageLimit limit = session.limit();
        store.sql("""
                        insert into primary_session (session_id, started_at, claude_pid, claude_start_tick, claimed_at, turn_started_at,
                                                     turn_event_mark, reported_at, stops_blocked, typed_at, notification, notified_at,
                                                     limit_name, limit_resets_at, limit_reached_at, ended_at, sitrep_opened_at,
                                                     turn_ended_at, turn_ended_waiting)
                        values (:session, :started, :pid, :startTick, :claimed, :turnStarted, :eventMark, :reported, :stopsBlocked,
                                :typed, :notification, :notified, :limitName, :limitResets, :limitReached, :ended, :sitrepOpened,
                                :turnEnded, :turnEndedWaiting)
                        on conflict (session_id) do update set
                            claude_pid = excluded.claude_pid, claude_start_tick = excluded.claude_start_tick,
                            claimed_at = excluded.claimed_at, turn_started_at = excluded.turn_started_at,
                            turn_event_mark = excluded.turn_event_mark, reported_at = excluded.reported_at,
                            stops_blocked = excluded.stops_blocked, typed_at = excluded.typed_at, notification = excluded.notification,
                            notified_at = excluded.notified_at, limit_name = excluded.limit_name, limit_resets_at = excluded.limit_resets_at,
                            limit_reached_at = excluded.limit_reached_at, ended_at = excluded.ended_at,
                            sitrep_opened_at = excluded.sitrep_opened_at, turn_ended_at = excluded.turn_ended_at,
                            turn_ended_waiting = excluded.turn_ended_waiting""")
                .param("session", session.id().toString())
                .param("started", Columns.text(session.startedAt()))
                .param("pid", process == null ? null : process.pid())
                .param("startTick", process == null ? null : process.startTick())
                .param("claimed", Columns.text(session.claimedAt()))
                .param("turnStarted", turn == null ? null : Columns.text(turn.startedAt()))
                .param("eventMark", turn == null ? null : turn.eventMark())
                .param("reported", turn == null ? null : Columns.text(turn.reportedAt()))
                .param("stopsBlocked", turn == null ? null : turn.stopsBlocked())
                .param("typed", Columns.text(session.typedAt()))
                .param("notification", session.notification())
                .param("notified", Columns.text(session.notifiedAt()))
                .param("limitName", limit == null ? null : limit.name())
                .param("limitResets", limit == null ? null : Columns.text(limit.resetsAt()))
                .param("limitReached", Columns.text(session.limitReachedAt()))
                .param("ended", Columns.text(session.endedAt()))
                .param("sitrepOpened", Columns.text(session.sitrepOpenedAt()))
                .param("turnEnded", turn == null ? null : Columns.text(turn.endedAt()))
                .param("turnEndedWaiting", turn != null && turn.endedWithAWait() ? 1 : 0)
                .update();
    }

    public Optional<PrimarySession> get(UUID session) {
        return sessions(store.sql(SELECT_SESSIONS + "where session_id = :session").param("session", session.toString())).stream().findFirst();
    }

    public List<PrimarySession> running() {
        return sessions(store.sql(SELECT_SESSIONS + "where ended_at is null"));
    }

    public List<PrimarySession> all() {
        return sessions(store.sql(SELECT_SESSIONS));
    }

    private List<PrimarySession> sessions(StatementSpec query) {
        return query.query((row, _) -> session(row)).list();
    }

    private static PrimarySession session(ResultSet row) throws SQLException {
        return new PrimarySession(
                UUID.fromString(row.getString("session_id")),
                Columns.instant(row.getString("started_at")),
                process(row),
                Columns.instant(row.getString("claimed_at")),
                turn(row),
                Columns.instant(row.getString("typed_at")),
                row.getString("notification"),
                Columns.instant(row.getString("notified_at")),
                limit(row),
                Columns.instant(row.getString("limit_reached_at")),
                Columns.instant(row.getString("ended_at")),
                Columns.instant(row.getString("sitrep_opened_at")));
    }

    private static SessionProcess process(ResultSet row) throws SQLException {
        long pid = row.getLong("claude_pid");
        return row.wasNull() ? null : new SessionProcess(pid, startTickIn(row));
    }

    private static Long startTickIn(ResultSet row) throws SQLException {
        long tick = row.getLong("claude_start_tick");
        return row.wasNull() ? null : tick;
    }

    private static PrimaryTurn turn(ResultSet row) throws SQLException {
        String started = row.getString("turn_started_at");
        if (started == null) {
            return null;
        }
        return new PrimaryTurn(Columns.instant(started), row.getLong("turn_event_mark"), Columns.instant(row.getString("reported_at")),
                row.getInt("stops_blocked"), Columns.instant(row.getString("turn_ended_at")), row.getBoolean("turn_ended_waiting"));
    }

    private static UsageLimit limit(ResultSet row) throws SQLException {
        String name = row.getString("limit_name");
        return name == null ? null : new UsageLimit(name, Columns.instant(row.getString("limit_resets_at")));
    }
}
