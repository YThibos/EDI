package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.delivery.CloneRefresh;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.TaskId;

@Component
public class CloneRefreshStore {

    private static final String SELECT_REFRESHES =
            "select task_id, project, base, expected_commit, requested_at, verified_at, clean_up_tried_at from clone_refresh ";

    private final JdbcClient store;

    public CloneRefreshStore(JdbcClient store) {
        this.store = store;
    }

    public void save(CloneRefresh refresh) {
        store.sql("""
                        insert into clone_refresh (task_id, project, base, expected_commit, requested_at, verified_at, clean_up_tried_at)
                        values (:task, :project, :base, :commit, :requested, :verified, :tried)
                        on conflict (task_id) do update set project = excluded.project, base = excluded.base,
                            expected_commit = excluded.expected_commit, requested_at = excluded.requested_at, verified_at = excluded.verified_at,
                            clean_up_tried_at = excluded.clean_up_tried_at""")
                .param("task", refresh.task().value())
                .param("project", refresh.project().value())
                .param("base", refresh.base().name())
                .param("commit", refresh.expectedCommit())
                .param("requested", Columns.text(refresh.requestedAt()))
                .param("verified", Columns.text(refresh.verifiedAt()))
                .param("tried", Columns.text(refresh.cleanUpTriedAt()))
                .update();
    }

    public List<CloneRefresh> pending() {
        return store.sql(SELECT_REFRESHES + "where verified_at is null order by requested_at").query((row, _) -> refreshOf(row)).list();
    }

    public List<CloneRefresh> pendingFor(ProjectName project) {
        return store.sql(SELECT_REFRESHES + "where project = :project and verified_at is null order by requested_at")
                .param("project", project.value())
                .query((row, _) -> refreshOf(row))
                .list();
    }

    public List<CloneRefresh> verifiedAwaitingCleanUp() {
        return store.sql(SELECT_REFRESHES + "where verified_at is not null and clean_up_tried_at is null order by verified_at")
                .query((row, _) -> refreshOf(row))
                .list();
    }

    private static CloneRefresh refreshOf(ResultSet row) throws SQLException {
        return new CloneRefresh(
                new TaskId(row.getString("task_id")),
                new ProjectName(row.getString("project")),
                new BaseBranch(row.getString("base")),
                row.getString("expected_commit"),
                Columns.instant(row.getString("requested_at")),
                Columns.instant(row.getString("verified_at")),
                Columns.instant(row.getString("clean_up_tried_at")));
    }
}
