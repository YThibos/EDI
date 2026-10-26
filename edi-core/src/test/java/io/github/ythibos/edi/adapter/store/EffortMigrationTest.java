package io.github.ythibos.edi.adapter.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.ythibos.edi.domain.dispatch.CaptainOverride;
import io.github.ythibos.edi.domain.dispatch.Dispatch;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.WorkerId;

class EffortMigrationTest {

    private static final String LAUNCHED = "2026-10-26T09:00:00Z";
    private static final TaskId LAMP = new TaskId("fit-lamp");
    private static final UUID SESSION = UUID.randomUUID();

    @TempDir
    private Path directory;

    @Test
    void keepsEveryDispatchWorkerAndCaptainsWordWithWhatHangsOnThemAndStoresEffortAboveHigh() throws IOException {
        DataSource store = StoreFile.openedAt(directory.resolve("edi.db"));
        Flyway.configure().dataSource(store).target("15").load().migrate();
        JdbcClient jdbc = JdbcClient.create(store);
        storeALaunchedLampAtHigh(jdbc);
        storeTheCaptainsWordOnTheLamp(jdbc);

        Flyway.configure().dataSource(store).load().migrate();
        DispatchStore dispatches = new DispatchStore(jdbc);
        Dispatch kept = dispatches.of(LAMP).orElseThrow();
        dispatches.save(kept.overriddenBy(new CaptainOverride(new Route(new ModelId("claude-opus-5-5"), EffortLevel.XHIGH), "the captain asked")));
        jdbc.sql("update worker set effort = 'max'").update();

        assertThat(kept.captainsOverride().route().effort()).isEqualTo(EffortLevel.HIGH);
        assertThat(kept.expectedChange().files()).containsExactly("lamp.txt");
        assertThat(dispatches.of(LAMP).orElseThrow().captainsOverride().route().effort()).isEqualTo(EffortLevel.XHIGH);
        assertThat(new WorkerStore(jdbc).get(WorkerId.parse("fit-lamp#1")).orElseThrow().route().effort()).isEqualTo(EffortLevel.MAX);
        assertThat(jdbc.sql("select count(*) from outage_entry").query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("pragma foreign_key_check").query((row, _) -> row.getString(1)).list()).isEqualTo(List.of());
        assertThatThrownBy(() -> jdbc.sql("update worker set effort = 'ultra'").update()).hasMessageContaining("CHECK constraint failed");
        assertThat(new CaptainWordStore(jdbc).heardIn(SESSION)).singleElement()
                .satisfies(word -> assertThat(word.isGeneral()).isTrue())
                .satisfies(word -> assertThat(word.verbatim()).isEqualTo("Drop the lamp."));
        assertThatThrownBy(() -> jdbc.sql("update captain_word set act = 'drop'").update()).hasMessageContaining("CHECK constraint failed");
    }

    private static void storeALaunchedLampAtHigh(JdbcClient jdbc) {
        jdbc.sql("insert into task (id, kind, title, state, work_key, notes, created_at) values ('fit-lamp', 'ship', 'Fit the lamp', 'in-flight', 'HRN-3', '', :at)")
                .param("at", LAUNCHED).update();
        jdbc.sql("""
                        insert into dispatch (task_id, category, branch, spec, expected_change, override_model, override_effort, override_reason, requested_at)
                        values ('fit-lamp', 'mechanical', 'chore/HRN-3', 'Fit it.', 'Fit it.', 'claude-opus-5-5', 'high', 'the captain asked', :at)""")
                .param("at", LAUNCHED).update();
        jdbc.sql("insert into dispatch_file (task_id, position, path) values ('fit-lamp', 0, 'lamp.txt')").update();
        jdbc.sql("""
                        insert into worker (id, task_id, incarnation, session_id, launch_kind, category, model, effort, token_hash, tmux_socket, server_pid,
                                            server_started_at, window_id, pane_pid, activity, activity_at, launched_at, context_full_since)
                        values ('fit-lamp#1', 'fit-lamp', 1, :session, 'dispatch', 'mechanical', 'claude-opus-5-5', 'high', 'digest-1', 'edi', 42,
                                :at, '@1', 43, 'busy', :at, :at, :at)""")
                .param("session", UUID.randomUUID().toString()).param("at", LAUNCHED).update();
        jdbc.sql("insert into worker_notice (worker_id, kind, since, noticed_at) values ('fit-lamp#1', 'died', :at, :at)").param("at", LAUNCHED).update();
        jdbc.sql("insert into outage_entry (number, task_id, worker_id, cause, listed_at) values (1, 'fit-lamp', 'fit-lamp#1', 'window-closed', :at)")
                .param("at", LAUNCHED).update();
    }

    private static void storeTheCaptainsWordOnTheLamp(JdbcClient jdbc) {
        jdbc.sql("insert into primary_session (session_id, started_at) values (:session, :at)").param("session", SESSION.toString()).param("at", LAUNCHED)
                .update();
        jdbc.sql("insert into captain_word (session_id, task_id, verbatim, said_at) values (:session, 'fit-lamp', 'Drop the lamp.', :at)")
                .param("session", SESSION.toString()).param("at", LAUNCHED).update();
    }
}
