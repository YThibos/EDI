package io.github.ythibos.edi.adapter.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerActivity;
import io.github.ythibos.edi.domain.worker.WorkerId;

class SupervisionMigrationTest {

    private static final String LAUNCHED = "2026-10-16T09:00:00Z";
    private static final String BRIEFED = "2026-10-16T09:00:20Z";

    @TempDir
    private Path directory;

    @Test
    void keepsEveryWorkerOfTheWorkersSchemaAndLetsAResumeShareItsSession() throws IOException {
        DataSource store = StoreFile.openedAt(directory.resolve("edi.db"));
        Flyway.configure().dataSource(store).target("2").load().migrate();
        JdbcClient jdbc = JdbcClient.create(store);
        UUID session = UUID.randomUUID();
        jdbc.sql("insert into task (id, kind, title, state, notes, created_at) values ('fit-lamp', 'ship', 'Fit the lamp', 'in-flight', '', :at)")
                .param("at", LAUNCHED).update();
        jdbc.sql("""
                        insert into worker (id, task_id, incarnation, session_id, category, model, effort, token_hash, tmux_socket, server_pid,
                                            server_started_at, window_id, pane_pid, activity, launched_at, started_at, briefed_at, status_state,
                                            status_text, status_at)
                        values ('fit-lamp#1', 'fit-lamp', 1, :session, 'implementation', 'claude-sonnet-5-5', 'high', 'digest-1', 'edi', 42,
                                :launched, '@1', 43, 'busy', :launched, :launched, :briefed, 'working', 'Fitting the lamp.', :briefed)""")
                .param("session", session.toString()).param("launched", LAUNCHED).param("briefed", BRIEFED).update();

        Flyway.configure().dataSource(store).load().migrate();
        Worker migrated = new WorkerStore(jdbc).get(WorkerId.parse("fit-lamp#1")).orElseThrow();
        jdbc.sql("""
                        insert into worker (id, task_id, incarnation, session_id, launch_kind, category, model, token_hash, tmux_socket, server_pid,
                                            server_started_at, window_id, pane_pid, activity, activity_at, launched_at)
                        values ('fit-lamp#2', 'fit-lamp', 2, :session, 'resume', 'implementation', 'claude-sonnet-5-5', 'digest-2', 'edi', 44,
                                :launched, '@2', 45, 'starting', :launched, :launched)""")
                .param("session", session.toString()).param("launched", LAUNCHED).update();

        assertThat(migrated.session()).isEqualTo(session);
        assertThat(migrated.launch().kind()).isEqualTo(LaunchKind.DISPATCH);
        assertThat(migrated.launch().boot()).isNull();
        assertThat(migrated.activity()).isEqualTo(WorkerActivity.BUSY);
        assertThat(migrated.activitySince()).isEqualTo(Instant.parse(BRIEFED));
        assertThat(migrated.lastStatus().report().text()).isEqualTo("Fitting the lamp.");
        assertThat(new WorkerStore(jdbc).latestOf(new TaskId("fit-lamp")).orElseThrow().session()).isEqualTo(session);
    }
}
