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
import io.github.ythibos.edi.domain.worker.BootId;
import io.github.ythibos.edi.domain.worker.DoneCheck;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.LimitPark;
import io.github.ythibos.edi.domain.worker.LoginName;
import io.github.ythibos.edi.domain.worker.UsageLimit;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.Worker.ReportedStatus;
import io.github.ythibos.edi.domain.worker.WorkerActivity;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.domain.worker.WorkerLaunch;
import io.github.ythibos.edi.domain.worker.WorkerWindow;

@Component
public class WorkerStore {

    private static final String SELECT_WORKERS = """
            select task_id, incarnation, session_id, launch_kind, boot_id, category, model, effort, tmux_socket, server_pid,
                   server_started_at, window_id, pane_pid, activity, activity_at, launched_at, started_at, briefed_at, transcript_path,
                   ended_at, stop_requested_at, launch_failed_at, retired_at, status_state, status_key, status_until, status_text,
                   status_at, done_check, limit_name, limit_resets_at, limit_parked_at, context_full_since, claude_login
            from worker
            """;

    private final JdbcClient store;

    public WorkerStore(JdbcClient store) {
        this.store = store;
    }

    public void insert(Worker worker, String tokenDigest) {
        WorkerWindow window = worker.window();
        store.sql("""
                        insert into worker (id, task_id, incarnation, session_id, launch_kind, boot_id, claude_login, category, model, effort,
                                            token_hash, tmux_socket, server_pid, server_started_at, window_id, pane_pid, activity, activity_at,
                                            launched_at)
                        values (:id, :task, :incarnation, :session, :launchKind, :boot, :login, :category, :model, :effort, :token, :socket,
                                :serverPid, :serverStarted, :window, :panePid, :activity, :activityAt, :launched)""")
                .param("id", worker.id().toString())
                .param("task", worker.id().task().value())
                .param("incarnation", worker.id().incarnation())
                .param("session", worker.session().toString())
                .param("launchKind", worker.launch().kind().word())
                .param("boot", Columns.textOf(worker.launch().boot()))
                .param("login", worker.launch().login().value())
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
                .param("activityAt", Columns.text(worker.activitySince()))
                .param("launched", Columns.text(worker.launchedAt()))
                .update();
    }

    public void update(Worker worker) {
        ReportedStatus status = worker.lastStatus();
        StatusReport report = status == null ? null : status.report();
        LimitPark park = worker.limitPark();
        store.sql("""
                        update worker set session_id = :session, activity = :activity, activity_at = :activityAt, started_at = :started,
                            briefed_at = :briefed, transcript_path = :transcript, ended_at = :ended, stop_requested_at = :stopRequested,
                            launch_failed_at = :launchFailed, retired_at = :retired, status_state = :state, status_key = :key,
                            status_until = :until, status_text = :text, status_at = :statusAt, done_check = :doneCheck,
                            limit_name = :limitName, limit_resets_at = :limitResets, limit_parked_at = :limitParked,
                            context_full_since = :contextFull
                        where id = :id""")
                .param("id", worker.id().toString())
                .param("session", worker.session().toString())
                .param("activity", worker.activity().word())
                .param("activityAt", Columns.text(worker.activitySince()))
                .param("started", Columns.text(worker.startedAt()))
                .param("briefed", Columns.text(worker.briefedAt()))
                .param("transcript", worker.transcript())
                .param("ended", Columns.text(worker.endedAt()))
                .param("stopRequested", Columns.text(worker.stopRequestedAt()))
                .param("launchFailed", Columns.text(worker.launchFailedAt()))
                .param("retired", Columns.text(worker.retiredAt()))
                .param("state", report == null ? null : report.state().word())
                .param("key", report == null ? null : Columns.textOf(report.key()))
                .param("until", report == null ? null : Columns.text(report.until()))
                .param("text", report == null ? null : report.text())
                .param("statusAt", status == null ? null : Columns.text(status.at()))
                .param("doneCheck", worker.doneCheck() == null ? null : worker.doneCheck().word())
                .param("limitName", park == null ? null : park.limit().name())
                .param("limitResets", park == null ? null : Columns.text(park.limit().resetsAt()))
                .param("limitParked", park == null ? null : Columns.text(park.parkedAt()))
                .param("contextFull", Columns.text(worker.contextFullSince()))
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

    public List<Worker> allOf(TaskId task) {
        return workers(store.sql(SELECT_WORKERS + "where task_id = :task order by incarnation").param("task", task.value()));
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
                launch(row),
                WorkerActivity.named(row.getString("activity")),
                Columns.instant(row.getString("activity_at")),
                Columns.instant(row.getString("started_at")),
                Columns.instant(row.getString("briefed_at")),
                row.getString("transcript_path"),
                Columns.instant(row.getString("ended_at")),
                Columns.instant(row.getString("stop_requested_at")),
                Columns.instant(row.getString("launch_failed_at")),
                Columns.instant(row.getString("retired_at")),
                reportedStatus(row),
                doneCheck == null ? null : DoneCheck.named(doneCheck),
                limitPark(row),
                Columns.instant(row.getString("context_full_since")));
    }

    private static WorkerLaunch launch(ResultSet row) throws SQLException {
        String boot = row.getString("boot_id");
        return new WorkerLaunch(LaunchKind.named(row.getString("launch_kind")), Columns.instant(row.getString("launched_at")),
                boot == null ? null : new BootId(boot), new LoginName(row.getString("claude_login")));
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

    private static LimitPark limitPark(ResultSet row) throws SQLException {
        String name = row.getString("limit_name");
        if (name == null) {
            return null;
        }
        return new LimitPark(new UsageLimit(name, Columns.instant(row.getString("limit_resets_at"))), Columns.instant(row.getString("limit_parked_at")));
    }
}
