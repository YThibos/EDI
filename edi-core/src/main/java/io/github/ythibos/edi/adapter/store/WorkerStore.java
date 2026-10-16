package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.DoneCheck;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.Worker.ReportedStatus;
import io.github.ythibos.edi.domain.worker.WorkerActivity;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.domain.worker.WorkerWindow;

@Component
public class WorkerStore {

    private static final String SELECT_WORKERS = """
            select task_id, incarnation, session_id, category, model, effort, tmux_socket, server_pid, server_started_at,
                   window_id, pane_pid, activity, launched_at, started_at, briefed_at, launch_failed_at, retired_at,
                   status_state, status_key, status_until, status_text, status_at, done_check
            from worker
            """;

    private final JdbcClient store;

    public WorkerStore(JdbcClient store) {
        this.store = store;
    }

    public void insert(Worker worker, String tokenDigest) {
        WorkerWindow window = worker.window();
        store.sql("""
                        insert into worker (id, task_id, incarnation, session_id, category, model, effort, token_hash, tmux_socket,
                                            server_pid, server_started_at, window_id, pane_pid, activity, launched_at)
                        values (:id, :task, :incarnation, :session, :category, :model, :effort, :token, :socket,
                                :serverPid, :serverStarted, :window, :panePid, :activity, :launched)""")
                .param("id", worker.id().toString())
                .param("task", worker.id().task().value())
                .param("incarnation", worker.id().incarnation())
                .param("session", worker.session().toString())
                .param("category", worker.category().value())
                .param("model", worker.route().model().value())
                .param("effort", worker.route().namesAnEffort() ? worker.route().effort().word() : null)
                .param("token", tokenDigest)
                .param("socket", window.socket())
                .param("serverPid", window.serverPid())
                .param("serverStarted", Columns.text(window.serverStartedAt()))
                .param("window", window.windowId())
                .param("panePid", window.panePid())
                .param("activity", worker.activity().word())
                .param("launched", Columns.text(worker.launchedAt()))
                .update();
    }

    public void update(Worker worker) {
        ReportedStatus status = worker.lastStatus();
        StatusReport report = status == null ? null : status.report();
        store.sql("""
                        update worker set activity = :activity, started_at = :started, briefed_at = :briefed,
                            launch_failed_at = :launchFailed, retired_at = :retired, status_state = :state, status_key = :key,
                            status_until = :until, status_text = :text, status_at = :statusAt, done_check = :doneCheck
                        where id = :id""")
                .param("id", worker.id().toString())
                .param("activity", worker.activity().word())
                .param("started", Columns.text(worker.startedAt()))
                .param("briefed", Columns.text(worker.briefedAt()))
                .param("launchFailed", Columns.text(worker.launchFailedAt()))
                .param("retired", Columns.text(worker.retiredAt()))
                .param("state", report == null ? null : report.state().word())
                .param("key", report == null ? null : Columns.textOf(report.key()))
                .param("until", report == null ? null : Columns.text(report.until()))
                .param("text", report == null ? null : report.text())
                .param("statusAt", status == null ? null : Columns.text(status.at()))
                .param("doneCheck", worker.doneCheck() == null ? null : worker.doneCheck().word())
                .update();
    }

    public Optional<Worker> get(WorkerId id) {
        return workers(store.sql(SELECT_WORKERS + "where id = :id").param("id", id.toString())).stream().findFirst();
    }

    public Optional<Worker> latestOf(TaskId task) {
        return workers(store.sql(SELECT_WORKERS + "where task_id = :task order by incarnation desc limit 1").param("task", task.value()))
                .stream()
                .findFirst();
    }

    public Optional<Worker> holdingToken(String tokenDigest) {
        return workers(store.sql(SELECT_WORKERS + "where token_hash = :token and retired_at is null").param("token", tokenDigest))
                .stream()
                .findFirst();
    }

    public List<Worker> serving() {
        return workers(store.sql(SELECT_WORKERS + "where retired_at is null order by launched_at, rowid"));
    }

    private List<Worker> workers(StatementSpec query) {
        return query.query((row, _) -> worker(row)).list();
    }

    private static Worker worker(ResultSet row) throws SQLException {
        String effort = row.getString("effort");
        String doneCheck = row.getString("done_check");
        return new Worker(
                new WorkerId(new TaskId(row.getString("task_id")), row.getInt("incarnation")),
                UUID.fromString(row.getString("session_id")),
                new CategoryId(row.getString("category")),
                new Route(new ModelId(row.getString("model")), effort == null ? null : EffortLevel.named(effort)),
                new WorkerWindow(
                        row.getString("tmux_socket"),
                        row.getLong("server_pid"),
                        Columns.instant(row.getString("server_started_at")),
                        row.getString("window_id"),
                        row.getLong("pane_pid")),
                WorkerActivity.named(row.getString("activity")),
                Columns.instant(row.getString("launched_at")),
                Columns.instant(row.getString("started_at")),
                Columns.instant(row.getString("briefed_at")),
                Columns.instant(row.getString("launch_failed_at")),
                Columns.instant(row.getString("retired_at")),
                reportedStatus(row),
                doneCheck == null ? null : DoneCheck.named(doneCheck));
    }

    private static ReportedStatus reportedStatus(ResultSet row) throws SQLException {
        String state = row.getString("status_state");
        if (state == null) {
            return null;
        }
        String key = row.getString("status_key");
        StatusReport report = new StatusReport(
                StatusState.named(state),
                key == null ? null : new StatusKey(key),
                Columns.instant(row.getString("status_until")),
                row.getString("status_text"));
        return new ReportedStatus(report, Columns.instant(row.getString("status_at")));
    }
}
