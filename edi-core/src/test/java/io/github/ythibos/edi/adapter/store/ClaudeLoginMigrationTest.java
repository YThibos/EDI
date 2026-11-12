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

import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.LoginName;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.domain.worker.WorkerLaunch;
import io.github.ythibos.edi.domain.worker.WorkerWindow;

class ClaudeLoginMigrationTest {

    private static final String LAUNCHED = "2026-11-09T09:00:00Z";

    @TempDir
    private Path directory;

    @Test
    void readsAWorkerLaunchedBeforeItsLoginWasRecordedAsTheDefaultLoginAndKeepsTheNameOfANewWorkersLogin() throws IOException {
        DataSource store = StoreFile.openedAt(directory.resolve("edi.db"));
        Flyway.configure().dataSource(store).target("24").load().migrate();
        JdbcClient jdbc = JdbcClient.create(store);
        storeALampLaunchedBeforeTheMigration(jdbc);

        Flyway.configure().dataSource(store).load().migrate();
        WorkerStore workers = new WorkerStore(jdbc);
        Worker kite = workerOnTheLogin(new LoginName("second"));
        workers.insert(kite, "digest-2");

        assertThat(workers.get(WorkerId.parse("fit-lamp#1")).orElseThrow().launch().login()).isEqualTo(LoginName.DEFAULT);
        assertThat(workers.get(kite.id()).orElseThrow().launch().login()).isEqualTo(new LoginName("second"));
        assertThat(jdbc.sql("select claude_login from worker where id = 'fly-kite#1'").query(String.class).single()).isEqualTo("second");
    }

    private static void storeALampLaunchedBeforeTheMigration(JdbcClient jdbc) {
        jdbc.sql("insert into task (id, kind, title, state, work_key, notes, created_at) values ('fit-lamp', 'ship', 'Fit the lamp', 'in-flight', 'HRN-3', '', :at)")
                .param("at", LAUNCHED).update();
        jdbc.sql("""
                        insert into worker (id, task_id, incarnation, session_id, launch_kind, category, model, effort, token_hash, tmux_socket, server_pid,
                                            server_started_at, window_id, pane_pid, activity, activity_at, launched_at)
                        values ('fit-lamp#1', 'fit-lamp', 1, :session, 'dispatch', 'implementation', 'claude-opus-5-5', 'high', 'digest-1', 'edi', 42,
                                :at, '@1', 43, 'busy', :at, :at)""")
                .param("session", UUID.randomUUID().toString()).param("at", LAUNCHED).update();
        jdbc.sql("insert into task (id, kind, title, state, work_key, notes, created_at) values ('fly-kite', 'ship', 'Fly the kite', 'in-flight', 'HRN-5', '', :at)")
                .param("at", LAUNCHED).update();
    }

    private static Worker workerOnTheLogin(LoginName login) {
        Instant at = Instant.parse(LAUNCHED);
        return Worker.launched(WorkerId.first(new TaskId("fly-kite")), UUID.randomUUID(), CategoryId.IMPLEMENTATION,
                new Route(new ModelId("claude-opus-5-5"), EffortLevel.HIGH), new WorkerWindow("edi", 44L, at, "@2", 45L),
                new WorkerLaunch(LaunchKind.DISPATCH, at, null, login));
    }
}
