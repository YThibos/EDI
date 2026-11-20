package io.github.ythibos.edi.adapter.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.ythibos.edi.domain.delivery.TaskCost;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.task.TaskId;

class CostRouteMigrationTest {

    private static final String COUNTED = "2026-11-16T10:00:00Z";

    @TempDir
    private Path directory;

    @Test
    void givesACostCountedBeforeTheMigrationTheRouteOfItsTasksFirstWorker() throws IOException {
        DataSource store = StoreFile.openedAt(directory.resolve("edi.db"));
        Flyway.configure().dataSource(store).target("31").load().migrate();
        JdbcClient jdbc = JdbcClient.create(store);
        storeATaskWithItsCost(jdbc, "fit-lamp");
        storeAWorker(jdbc, "fit-lamp", 1, "claude-opus-5-5", "xhigh");
        storeAWorker(jdbc, "fit-lamp", 2, "claude-sonnet-5-5", "high");
        storeATaskWithItsCost(jdbc, "paint-buoy");
        storeAWorker(jdbc, "paint-buoy", 1, "claude-sonnet-5-5", null);
        storeATaskWithItsCost(jdbc, "chart-reef");

        Flyway.configure().dataSource(store).load().migrate();
        TaskCostStore costs = new TaskCostStore(jdbc);

        assertThat(costs.of(new TaskId("fit-lamp"))).map(TaskCost::route).contains(new Route(new ModelId("claude-opus-5-5"), EffortLevel.XHIGH));
        assertThat(costs.of(new TaskId("paint-buoy"))).map(TaskCost::route).contains(new Route(new ModelId("claude-sonnet-5-5"), null));
        assertThat(costs.of(new TaskId("chart-reef")).orElseThrow().routeIfKnown()).isEmpty();
    }

    private static void storeATaskWithItsCost(JdbcClient jdbc, String task) {
        jdbc.sql("insert into task (id, kind, title, stage, notes, created_at, closed_at) values (:task, 'ship', :task, 'retired', '', :at, :at)")
                .param("task", task).param("at", COUNTED).update();
        jdbc.sql("""
                        insert into task_cost (task_id, worker_input_tokens, worker_cache_read_tokens, worker_cache_write_tokens, worker_output_tokens, counted_at)
                        values (:task, 1, 2, 3, 4, :at)""")
                .param("task", task).param("at", COUNTED).update();
    }

    private static void storeAWorker(JdbcClient jdbc, String task, int incarnation, String model, String effort) {
        jdbc.sql("""
                        insert into worker (id, task_id, incarnation, session_id, launch_kind, category, model, effort, token_hash, tmux_socket, server_pid,
                                            server_started_at, window_id, pane_pid, activity, activity_at, launched_at)
                        values (:id, :task, :incarnation, :session, 'dispatch', 'implementation', :model, :effort, :token, 'edi', 42,
                                :at, '@1', 43, 'idle', :at, :at)""")
                .param("id", task + "#" + incarnation).param("task", task).param("incarnation", incarnation)
                .param("session", UUID.randomUUID().toString()).param("model", model).param("effort", effort)
                .param("token", "digest-" + task + "-" + incarnation).param("at", COUNTED).update();
    }
}
