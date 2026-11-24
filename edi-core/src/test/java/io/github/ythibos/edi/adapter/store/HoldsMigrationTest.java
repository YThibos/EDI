package io.github.ythibos.edi.adapter.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.task.Hold;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.TaskId;

class HoldsMigrationTest {

    private static final String CREATED = "2026-11-20T09:00:00Z";
    private static final String HELD = "2026-11-21T09:00:00Z";
    private static final String OUTAGE_REASON =
            "lost when the machine restarted; number 2 on the outage hold list waits for the captain to resume it or start it fresh";

    @TempDir
    private Path directory;

    private JdbcClient jdbc;
    private DataSource store;

    @BeforeEach
    void storeATaskHeldInEveryWayBeforeTheHoldsWereASet() throws IOException {
        store = StoreFile.openedAt(directory.resolve("edi.db"));
        Flyway.configure().dataSource(store).target("34").load().migrate();
        jdbc = JdbcClient.create(store);
        task("fit-horn", "queued", null);
        hold("fit-horn", "deferral", "after the season", "2026-12-01");
        task("paint-mast", "queued", null);
        hold("paint-mast", "approval", "needs the keeper's yes", null);
        task("fit-lamp", "working", null);
        worker("fit-lamp#1", "fit-lamp");
        jdbc.sql("insert into outage_entry (number, task_id, worker_id, cause, listed_at) values (2, 'fit-lamp', 'fit-lamp#1', 'machine-restarted', :at)")
                .param("at", HELD).update();
        hold("fit-lamp", "approval", OUTAGE_REASON, null);
        task("choose-oil", "working", null);
        decision(57, "choose-oil", "2026-11-19T09:00:00Z", "A");
        decision(58, "choose-oil", HELD, null);
        hold("choose-oil", "decision", "D58 Which oil?", null);
        task("old-bell", "dropped", "2026-11-22T09:00:00Z");
        decision(59, "old-bell", HELD, null);
    }

    @Test
    void keepsEveryHoldAndGivesTheOutageEntryItsOwnKind() {
        Flyway.configure().dataSource(store).load().migrate();
        TaskStore tasks = new TaskStore(jdbc);

        assertThat(tasks.get(new TaskId("fit-horn")).holds().held())
                .containsExactly(new Hold.Deferral("after the season", HoldUntil.day(LocalDate.of(2026, 12, 1)), Instant.parse(HELD)));
        assertThat(tasks.get(new TaskId("paint-mast")).holds().held())
                .containsExactly(new Hold.Approval("needs the keeper's yes", HoldUntil.captainsWord(), Instant.parse(HELD)));
        assertThat(tasks.get(new TaskId("fit-lamp")).holds().held())
                .containsExactly(new Hold.Outage(2, "lost when the machine restarted", Instant.parse(HELD)));
        assertThat(tasks.get(new TaskId("fit-lamp")).reasonsItWaits()).isEqualTo(OUTAGE_REASON);
    }

    @Test
    void holdsATaskForEachOfItsOpenDecisionsOnly() {
        Flyway.configure().dataSource(store).load().migrate();
        TaskStore tasks = new TaskStore(jdbc);

        assertThat(tasks.get(new TaskId("choose-oil")).holds().held()).containsExactly(new Hold.OpenDecision(new DecisionNumber(58), Instant.parse(HELD)));
        assertThat(tasks.get(new TaskId("old-bell")).isHeld()).isFalse();
        assertThat(jdbc.sql("select count(*) from sqlite_master where name = 'captain_hold'").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("pragma foreign_key_check").query((row, _) -> row.getString(1)).list()).isEqualTo(List.of());
    }

    private void task(String id, String stage, String closedAt) {
        jdbc.sql("insert into task (id, kind, title, stage, notes, created_at, closed_at) values (:id, 'ship', :id, :stage, '', :at, :closed)")
                .param("id", id).param("stage", stage).param("at", CREATED).param("closed", closedAt).update();
    }

    private void hold(String task, String kind, String reason, String until) {
        jdbc.sql("insert into captain_hold (task_id, kind, reason, until, set_at) values (:task, :kind, :reason, :until, :at)")
                .param("task", task).param("kind", kind).param("reason", reason).param("until", until).param("at", HELD).update();
    }

    private void worker(String id, String task) {
        jdbc.sql("""
                        insert into worker (id, task_id, incarnation, session_id, launch_kind, category, model, token_hash, tmux_socket, server_pid,
                                            server_started_at, window_id, pane_pid, activity, activity_at, launched_at, status_state, status_at)
                        values (:id, :task, 1, :session, 'dispatch', 'implementation', 'claude-opus-5-5', :id, 'edi', 42,
                                :at, '@1', 43, 'idle', :at, :at, 'working', :at)""")
                .param("id", id).param("task", task).param("session", UUID.randomUUID().toString()).param("at", CREATED).update();
    }

    private void decision(int number, String task, String raisedAt, String answer) {
        jdbc.sql("""
                        insert into decision (number, task_id, title, context, recommended, recommended_because, raised_at, answer_verbatim, answered_at,
                                              answer_channel)
                        values (:number, :task, 'Which oil?', '', 'A', 'it burns clean', :raised, :answer, :answered, :channel)""")
                .param("number", number).param("task", task).param("raised", raisedAt).param("answer", answer)
                .param("answered", answer == null ? null : raisedAt).param("channel", answer == null ? null : "chat").update();
    }
}
