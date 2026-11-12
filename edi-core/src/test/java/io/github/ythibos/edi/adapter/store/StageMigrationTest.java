package io.github.ythibos.edi.adapter.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.ythibos.edi.domain.task.StageReached;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskStage;
import io.github.ythibos.edi.domain.task.TaskState;
import tools.jackson.databind.json.JsonMapper;

class StageMigrationTest {

    private static final String CREATED = "2026-10-26T09:00:00Z";
    private static final String MERGED_HEAD = "7e23dd8036558de2ddd39208fcf4e456a39d85c5";
    private static final String STEERED_HEAD = "b30e15a9f1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6";

    @TempDir
    private Path directory;

    private JdbcClient jdbc;
    private DataSource store;

    @BeforeEach
    void storeATaskInEveryStateBeforeStages() throws IOException {
        store = StoreFile.openedAt(directory.resolve("edi.db"));
        Flyway.configure().dataSource(store).target("25").load().migrate();
        jdbc = JdbcClient.create(store);
        task("fit-horn", "ship", "queued", null);
        task("fit-lamp", "ship", "in-flight", null);
        worker("fit-lamp#1", "fit-lamp", 1, "working", null);
        task("sap-roundtrip-spec", "scout", "in-flight", null);
        worker("sap-roundtrip-spec#1", "sap-roundtrip-spec", 1, "done", "accepted");
        answeredDecision(38, "sap-roundtrip-spec");
        task("fit-mast", "ship", "in-flight", null);
        worker("fit-mast#1", "fit-mast", 1, "done", "accepted");
        event("2026-10-26T10:00:00Z", "StatusReported", "fit-mast", "{\"worker\":\"fit-mast#1\",\"state\":\"done\"}");
        jdbc.sql("update worker set retired_at = :at where id = 'fit-mast#1'").param("at", "2026-10-26T10:30:00Z").update();
        worker("fit-mast#2", "fit-mast", 2, "working", null);
        task("land-the-spec", "ship", "in-flight", null);
        worker("land-the-spec#1", "land-the-spec", 1, "working", null);
        pipelineRun("01RUNATTHEHEAD", "land-the-spec", MERGED_HEAD, "2026-10-26T15:10:02.880Z");
        pipelineRun("01RUNAFTERTHESTEER", "land-the-spec", STEERED_HEAD, "2026-10-26T15:36:18.000Z");
        mergedDelivery("land-the-spec", 107, MERGED_HEAD, "2026-10-26T15:38:37Z");
        event("2026-10-26T15:38:37Z", "DraftMerged", "land-the-spec", "{}");
        task("fit-bell", "ship", "in-flight", null);
        mergedDelivery("fit-bell", 108, "c0ffee0036558de2ddd39208fcf4e456a39d85c5", "2026-10-26T16:00:00Z");
        task("sail-away", "ship", "dropped", "2026-10-26T11:00:00Z");
        task("old-bell", "ship", "done", "2026-10-26T12:00:00Z");
        task("choose-lamp", "captain", "done", "2026-10-26T12:30:00Z");
    }

    @Test
    void derivesTheStageOfEveryTaskOnceFromWhatTheStoreKnew() {
        Flyway.configure().dataSource(store).load().migrate();
        TaskStore tasks = new TaskStore(jdbc);

        assertThat(tasks.ids()).extracting(TaskId::value).containsExactly("fit-horn", "fit-lamp", "sap-roundtrip-spec", "fit-mast", "land-the-spec",
                "fit-bell", "sail-away", "old-bell", "choose-lamp");
        assertThat(tasks.ids()).extracting(id -> tasks.get(id).stage()).containsExactly(TaskStage.QUEUED, TaskStage.WORKING, TaskStage.DELIVERED,
                TaskStage.CHANGING, TaskStage.MERGED, TaskStage.MERGED, TaskStage.DROPPED, TaskStage.RETIRED, TaskStage.RETIRED);
        assertThat(tasks.inState(TaskState.IN_FLIGHT)).extracting(task -> task.id().value())
                .containsExactly("fit-lamp", "sap-roundtrip-spec", "fit-mast", "land-the-spec", "fit-bell");
        assertThat(tasks.get(new TaskId("old-bell")).closed()).isEqualTo(Instant.parse("2026-10-26T12:00:00Z"));
        assertThat(jdbc.sql("pragma foreign_key_check").query((row, _) -> row.getString(1)).list()).isEqualTo(List.of());
    }

    @Test
    void namesTheCommitsTheBranchReachedAfterTheMergedHeadAsNotLanded() {
        Flyway.configure().dataSource(store).load().migrate();

        assertThat(stagesOf("land-the-spec")).containsExactly(new StageReached(Instant.parse("2026-10-26T15:38:37Z"), TaskStage.MERGED,
                "merged at " + MERGED_HEAD + "; 1 commit not landed: b30e15a9f1c2"));
        assertThat(jdbc.sql("select json_extract(payload, '$.notLanded[0].id') from event where type = 'StageDerived' and task_id = 'land-the-spec'")
                .query(String.class).single()).isEqualTo(STEERED_HEAD);
        assertThat(stagesOf("fit-bell")).extracting(StageReached::reason).containsExactly("merged at c0ffee0036558de2ddd39208fcf4e456a39d85c5");
    }

    @Test
    void recordsWhyEachTaskInFlightIsAtItsStageAndNothingForTheRest() {
        Flyway.configure().dataSource(store).load().migrate();

        assertThat(stagesOf("fit-lamp")).extracting(StageReached::stage, StageReached::reason)
                .containsExactly(tuple(TaskStage.WORKING, "dispatched"));
        assertThat(stagesOf("sap-roundtrip-spec")).extracting(StageReached::reason).containsExactly("its worker reported done");
        assertThat(stagesOf("fit-mast")).extracting(StageReached::at, StageReached::reason)
                .containsExactly(tuple(Instant.parse("2026-10-26T10:00:00Z"), "its worker works again after its done"));
        assertThat(List.of("fit-horn", "sail-away", "old-bell", "choose-lamp")).allSatisfy(id -> assertThat(stagesOf(id)).isEmpty());
    }

    @Test
    void keepsTheClosingTimeExactlyForARetiredOrDroppedTask() {
        Flyway.configure().dataSource(store).load().migrate();

        assertThatThrownBy(() -> jdbc.sql("update task set stage = 'retired' where id = 'fit-lamp'").update())
                .hasMessageContaining("CHECK constraint failed");
        assertThatThrownBy(() -> jdbc.sql("update task set stage = 'shipped' where id = 'fit-lamp'").update())
                .hasMessageContaining("CHECK constraint failed");
    }

    private List<StageReached> stagesOf(String task) {
        return new EventLog(jdbc, new JsonMapper(), new WakeStore(jdbc), _ -> { }).stagesOf(new TaskId(task));
    }

    private void task(String id, String kind, String state, String closedAt) {
        jdbc.sql("insert into task (id, kind, title, state, notes, created_at, closed_at) values (:id, :kind, :id, :state, '', :at, :closed)")
                .param("id", id).param("kind", kind).param("state", state).param("at", CREATED).param("closed", closedAt).update();
    }

    private void worker(String id, String task, int incarnation, String status, String doneCheck) {
        jdbc.sql("""
                        insert into worker (id, task_id, incarnation, session_id, launch_kind, category, model, token_hash, tmux_socket, server_pid,
                                            server_started_at, window_id, pane_pid, activity, activity_at, launched_at, status_state, status_at, done_check)
                        values (:id, :task, :incarnation, :session, 'dispatch', 'implementation', 'claude-opus-5-5', :id, 'edi', 42,
                                :at, '@1', 43, 'idle', :at, :at, :status, :at, :doneCheck)""")
                .param("id", id).param("task", task).param("incarnation", incarnation).param("session", UUID.randomUUID().toString())
                .param("at", CREATED).param("status", status).param("doneCheck", doneCheck).update();
    }

    private void answeredDecision(int number, String task) {
        jdbc.sql("""
                        insert into decision (number, task_id, title, context, recommended, recommended_because, raised_at, answer_verbatim, answered_at,
                                              answer_channel)
                        values (:number, :task, 'Which route?', '', 'A', 'it is shorter', :at, 'A', :at, 'chat')""")
                .param("number", number).param("task", task).param("at", CREATED).update();
    }

    private void event(String at, String type, String task, String payload) {
        jdbc.sql("insert into event (at, actor, type, task_id, payload) values (:at, 'core', :type, :task, :payload)")
                .param("at", at).param("type", type).param("task", task).param("payload", payload).update();
    }

    private void pipelineRun(String run, String task, String head, String seenAt) {
        jdbc.sql("""
                        insert into pipeline_run (run_id, task_id, status, head_commit, seen_at, observed_at)
                        values (:run, :task, 'completed', :head, :at, :at)""")
                .param("run", run).param("task", task).param("head", head).param("at", seenAt).update();
    }

    private void mergedDelivery(String task, int number, String head, String at) {
        jdbc.sql("""
                        insert into delivery (task_id, provider, host, path, number, url, draft, head_commit, state, landed_commit, recorded_at, observed_at)
                        values (:task, 'github', 'github.com', 'example-owner/harness', :number, :url, 0, :head, 'merged', 'f00d', :at, :at)""")
                .param("task", task).param("number", number).param("url", "https://github.com/example-owner/harness/pull/" + number)
                .param("head", head).param("at", at).update();
    }
}
