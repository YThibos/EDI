package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.dispatch.CaptainOverride;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.Dispatch;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.mechanical.ExpectedChange;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.TaskId;

@Component
public class DispatchStore {

    private static final String SELECT_DISPATCHES = """
            select task_id, category, branch, spec, expected_change, override_model, override_effort, override_reason,
                   requested_at, deferred_at
            from dispatch
            """;

    private final JdbcClient store;

    public DispatchStore(JdbcClient store) {
        this.store = store;
    }

    public void save(Dispatch dispatch) {
        store.sql("""
                        insert into dispatch (task_id, category, branch, spec, expected_change, override_model, override_effort,
                                              override_reason, requested_at, deferred_at)
                        values (:task, :category, :branch, :spec, :expected, :model, :effort, :reason, :requested, :deferred)
                        on conflict (task_id) do update set
                            category = excluded.category, branch = excluded.branch, spec = excluded.spec,
                            expected_change = excluded.expected_change, override_model = excluded.override_model,
                            override_effort = excluded.override_effort, override_reason = excluded.override_reason,
                            requested_at = excluded.requested_at, deferred_at = excluded.deferred_at""")
                .param("task", dispatch.task().value())
                .param("category", dispatch.category().value())
                .param("branch", dispatch.branch().toString())
                .param("spec", dispatch.spec())
                .param("expected", dispatch.expectedChange() == null ? null : dispatch.expectedChange().description())
                .param("model", dispatch.captainsOverride() == null ? null : dispatch.captainsOverride().route().model().value())
                .param("effort", overrideEffortOf(dispatch))
                .param("reason", dispatch.captainsOverride() == null ? null : dispatch.captainsOverride().reason())
                .param("requested", Columns.text(dispatch.requestedAt()))
                .param("deferred", Columns.text(dispatch.deferredAt()))
                .update();
        replaceExpectedFiles(dispatch);
    }

    public Optional<Dispatch> of(TaskId task) {
        return dispatches(store.sql(SELECT_DISPATCHES + "where task_id = :task").param("task", task.value())).stream().findFirst();
    }

    public int countIn(CategoryId category, WorkKey key, TaskId besides) {
        return store.sql("""
                        select count(*) from dispatch join task on task.id = dispatch.task_id
                        where dispatch.category = :category and task.work_key = :key and task.id != :besides""")
                .param("category", category.value())
                .param("key", key.toString())
                .param("besides", besides.value())
                .query((row, _) -> row.getInt(1))
                .single();
    }

    public Set<String> branchesOfOpenTasks() {
        return Set.copyOf(store.sql("""
                        select dispatch.branch from dispatch join task on task.id = dispatch.task_id
                        where task.state in ('queued', 'in-flight')""")
                .query(String.class)
                .list());
    }

    public List<BranchName> branchesOfTasksKeyedOn(WorkKey key) {
        return store.sql("select dispatch.branch from dispatch join task on task.id = dispatch.task_id where task.work_key = :key")
                .param("key", key.toString())
                .query((row, _) -> BranchName.parse(row.getString(1)))
                .list();
    }

    public List<Dispatch> deferred() {
        return dispatches(store.sql(SELECT_DISPATCHES + "where deferred_at is not null order by deferred_at, rowid"));
    }

    private static String overrideEffortOf(Dispatch dispatch) {
        CaptainOverride override = dispatch.captainsOverride();
        return override == null || !override.route().namesAnEffort() ? null : override.route().effort().word();
    }

    private void replaceExpectedFiles(Dispatch dispatch) {
        store.sql("delete from dispatch_file where task_id = :task").param("task", dispatch.task().value()).update();
        if (dispatch.expectedChange() == null) {
            return;
        }
        List<String> files = dispatch.expectedChange().files();
        IntStream.range(0, files.size()).forEach(position ->
                store.sql("insert into dispatch_file (task_id, position, path) values (:task, :position, :path)")
                        .param("task", dispatch.task().value())
                        .param("position", position)
                        .param("path", files.get(position))
                        .update());
    }

    private List<Dispatch> dispatches(StatementSpec query) {
        return query.query((row, _) -> dispatchRow(row)).list().stream().map(row -> row.dispatch(expectedFilesOf(row.task()))).toList();
    }

    private List<String> expectedFilesOf(String task) {
        return store.sql("select path from dispatch_file where task_id = :task order by position").param("task", task).query(String.class).list();
    }

    private static DispatchRow dispatchRow(ResultSet row) throws SQLException {
        return new DispatchRow(
                row.getString("task_id"),
                row.getString("category"),
                row.getString("branch"),
                row.getString("spec"),
                row.getString("expected_change"),
                row.getString("override_model"),
                row.getString("override_effort"),
                row.getString("override_reason"),
                row.getString("requested_at"),
                row.getString("deferred_at"));
    }

    private record DispatchRow(
            String task,
            String category,
            String branch,
            String spec,
            String expectedChange,
            String overrideModel,
            String overrideEffort,
            String overrideReason,
            String requested,
            String deferred) {

        Dispatch dispatch(List<String> expectedFiles) {
            return new Dispatch(
                    new TaskId(task),
                    new CategoryId(category),
                    BranchName.parse(branch),
                    spec,
                    expectedChange == null ? null : new ExpectedChange(expectedChange, expectedFiles),
                    captainsOverride(),
                    Columns.instant(requested),
                    Columns.instant(deferred));
        }

        private CaptainOverride captainsOverride() {
            if (overrideModel == null) {
                return null;
            }
            EffortLevel effort = overrideEffort == null ? null : EffortLevel.named(overrideEffort);
            return new CaptainOverride(new Route(new ModelId(overrideModel), effort), overrideReason);
        }
    }
}
