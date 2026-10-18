package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.presence.Mandate;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.presence.PresenceCause;
import io.github.ythibos.edi.domain.presence.PresenceMode;

@Component
public class PresenceStore {

    private final JdbcClient store;
    private final ApplicationEventPublisher publisher;

    public PresenceStore(JdbcClient store, ApplicationEventPublisher publisher) {
        this.store = store;
        this.publisher = publisher;
    }

    public Optional<Presence> current() {
        return store.sql("select mode, since, cause, mandate, worker_cap, demo_until from presence where id = 1")
                .query((row, _) -> presence(row))
                .optional();
    }

    public void save(Presence presence) {
        withColumnsOf(presence, store.sql("""
                        insert into presence (id, mode, since, cause, mandate, worker_cap, demo_until)
                        values (1, :mode, :since, :cause, :mandate, :cap, :demo)
                        on conflict (id) do update set mode = excluded.mode, since = excluded.since, cause = excluded.cause,
                            mandate = excluded.mandate, worker_cap = excluded.worker_cap, demo_until = excluded.demo_until"""))
                .update();
        publisher.publishEvent(new PresenceChanged(presence.mode()));
    }

    public void oweABriefFor(Presence left) {
        withColumnsOf(left, store.sql("""
                        insert into unbriefed_absence (id, mode, since, cause, mandate, worker_cap, demo_until)
                        values (1, :mode, :since, :cause, :mandate, :cap, :demo)
                        on conflict (id) do nothing"""))
                .update();
    }

    public Optional<Presence> unbriefedAbsence() {
        return store.sql("select mode, since, cause, mandate, worker_cap, demo_until from unbriefed_absence where id = 1")
                .query((row, _) -> presence(row))
                .optional();
    }

    public void forgetUnbriefedAbsence() {
        store.sql("delete from unbriefed_absence").update();
    }

    private static StatementSpec withColumnsOf(Presence presence, StatementSpec statement) {
        return statement
                .param("mode", presence.mode().word())
                .param("since", Columns.text(presence.since()))
                .param("cause", presence.cause().word())
                .param("mandate", presence.mandate() == null ? null : presence.mandate().words())
                .param("cap", presence.mandate() == null ? null : presence.mandate().workerCap())
                .param("demo", Columns.text(presence.demoUntil()));
    }

    private static Presence presence(ResultSet row) throws SQLException {
        String mandate = row.getString("mandate");
        return new Presence(
                PresenceMode.named(row.getString("mode")),
                Columns.instant(row.getString("since")),
                PresenceCause.named(row.getString("cause")),
                mandate == null ? null : new Mandate(mandate, row.getInt("worker_cap")),
                Columns.instant(row.getString("demo_until")));
    }
}
