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

import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;

class LastPromptMigrationTest {

    private static final String ASKED = "2026-11-24T09:00:00Z";
    private static final WorkerId LAMP = new WorkerId(new TaskId("fit-lamp"), 1);

    @TempDir
    private Path directory;

    @Test
    void keepsAWorkerStoredBeforeTheMigrationWaitingOnItsQuestionUntilAPromptReachesIt() throws IOException {
        DataSource store = StoreFile.openedAt(directory.resolve("edi.db"));
        Flyway.configure().dataSource(store).target("32").load().migrate();
        JdbcClient jdbc = JdbcClient.create(store);
        storeAWorkerAskingAQuestion(jdbc);

        Flyway.configure().dataSource(store).load().migrate();
        WorkerStore workers = new WorkerStore(jdbc);
        Worker migrated = workers.get(LAMP).orElseThrow();

        assertThat(migrated.lastPromptAt()).isNull();
        assertThat(migrated.lastReported(StatusState.NEEDS_DECISION)).isTrue();
        assertThat(migrated.waitsOnItsQuestion()).isTrue();

        Instant answered = Instant.parse("2026-11-24T09:05:00Z");
        workers.update(migrated.promptSubmitted(answered));

        assertThat(workers.get(LAMP).orElseThrow().lastPromptAt()).isEqualTo(answered);
        assertThat(workers.get(LAMP).orElseThrow().waitsOnItsQuestion()).isFalse();
    }

    private static void storeAWorkerAskingAQuestion(JdbcClient jdbc) {
        jdbc.sql("insert into task (id, kind, title, stage, notes, created_at) values ('fit-lamp', 'ship', 'Fit the lamp', 'working', '', :at)")
                .param("at", ASKED).update();
        jdbc.sql("""
                        insert into worker (id, task_id, incarnation, session_id, launch_kind, category, model, token_hash, tmux_socket, server_pid,
                                            server_started_at, window_id, pane_pid, activity, activity_at, launched_at, started_at, briefed_at,
                                            status_state, status_key, status_text, status_at)
                        values (:id, 'fit-lamp', 1, :session, 'dispatch', 'implementation', 'claude-opus-5-5', 'digest-fit-lamp', 'edi', 42,
                                :at, '@1', 43, 'idle', :at, :at, :at, :at, 'needs-decision', 'lamp-choice', 'Which lamp?', :at)""")
                .param("id", LAMP.toString()).param("session", UUID.randomUUID().toString()).param("at", ASKED).update();
    }
}
