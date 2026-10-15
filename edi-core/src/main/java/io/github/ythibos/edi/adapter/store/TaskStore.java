package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.decision.UnknownDecision;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.Blocker;
import io.github.ythibos.edi.domain.task.CaptainHold;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.domain.task.UnknownTask;

@Component
public class TaskStore {

    private static final String SELECT_TASKS = "select id, kind, title, state, work_key, base, notes, created_at, closed_at from task ";

    private final JdbcClient store;

    public TaskStore(JdbcClient store) {
        this.store = store;
    }

    public boolean contains(TaskId id) {
        return store.sql("select count(*) from task where id = :id")
                .param("id", id.value())
                .query(Integer.class)
                .single() > 0;
    }

    public void insert(Task task) {
        store.sql("""
                        insert into task (id, kind, title, state, work_key, base, notes, created_at, closed_at)
                        values (:id, :kind, :title, :state, :workKey, :base, :notes, :created, :closed)""")
                .param("id", task.id().value())
                .param("kind", task.intake().kind().word())
                .param("title", task.intake().title())
                .param("state", task.state().word())
                .param("workKey", Columns.textOf(task.intake().workKey()))
                .param("base", Columns.textOf(task.intake().base()))
                .param("notes", task.notes())
                .param("created", Columns.text(task.created()))
                .param("closed", Columns.text(task.closed()))
                .update();
        insertProjects(task);
        insertBlockers(task);
        replaceHold(task);
    }

    public void update(Task task) {
        store.sql("update task set state = :state, notes = :notes, closed_at = :closed where id = :id")
                .param("id", task.id().value())
                .param("state", task.state().word())
                .param("notes", task.notes())
                .param("closed", Columns.text(task.closed()))
                .update();
        replaceHold(task);
    }

    public Task get(TaskReference reference) {
        return get(switch (reference) {
            case TaskReference.ById byId -> byId.id();
            case TaskReference.ByDecision byDecision -> taskDecidedBy(byDecision.number());
        });
    }

    public Task get(TaskId id) {
        return tasks(store.sql(SELECT_TASKS + "where id = :id").param("id", id.value())).stream()
                .findFirst()
                .orElseThrow(() -> new UnknownTask(id));
    }

    public List<Task> inState(TaskState state) {
        return tasks(store.sql(SELECT_TASKS + "where state = :state order by rowid").param("state", state.word()));
    }

    public List<Blocker> blockersOf(Task task) {
        List<TaskId> blockedBy = task.intake().blockedBy();
        if (blockedBy.isEmpty()) {
            return List.of();
        }
        Map<String, TaskState> states = store.sql("select id, state from task where id in (:ids)")
                .param("ids", blockedBy.stream().map(TaskId::value).toList())
                .query((row, _) -> Map.entry(row.getString("id"), TaskState.named(row.getString("state"))))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return blockedBy.stream().map(blocker -> new Blocker(blocker, states.get(blocker.value()))).toList();
    }

    private void insertProjects(Task task) {
        List<ProjectName> projects = task.intake().projects();
        IntStream.range(0, projects.size()).forEach(position ->
                store.sql("insert into task_project (task_id, position, project) values (:task, :position, :project)")
                        .param("task", task.id().value())
                        .param("position", position)
                        .param("project", projects.get(position).value())
                        .update());
    }

    private void insertBlockers(Task task) {
        task.intake().blockedBy().forEach(blocker ->
                store.sql("insert into task_dependency (task_id, blocked_by) values (:task, :blocker)")
                        .param("task", task.id().value())
                        .param("blocker", blocker.value())
                        .update());
    }

    private void replaceHold(Task task) {
        store.sql("delete from captain_hold where task_id = :task").param("task", task.id().value()).update();
        if (task.isHeld()) {
            CaptainHold hold = task.hold();
            store.sql("insert into captain_hold (task_id, kind, reason, until, set_at) values (:task, :kind, :reason, :until, :setAt)")
                    .param("task", task.id().value())
                    .param("kind", hold.kind().word())
                    .param("reason", hold.reason())
                    .param("until", Columns.text(hold.until()))
                    .param("setAt", Columns.text(hold.setAt()))
                    .update();
        }
    }

    private TaskId taskDecidedBy(DecisionNumber number) {
        return store.sql("select task_id from decision where number = :number")
                .param("number", number.value())
                .query(String.class)
                .optional()
                .map(TaskId::new)
                .orElseThrow(() -> new UnknownDecision(number));
    }

    private List<Task> tasks(StatementSpec query) {
        List<TaskRow> rows = query.query((row, _) -> taskRow(row)).list();
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> ids = rows.stream().map(TaskRow::id).toList();
        TaskParts parts = new TaskParts(projectsOf(ids), blockerIdsOf(ids), holdsOf(ids));
        return rows.stream().map(row -> row.task(parts)).toList();
    }

    private static TaskRow taskRow(ResultSet row) throws SQLException {
        return new TaskRow(
                row.getString("id"),
                row.getString("kind"),
                row.getString("title"),
                row.getString("state"),
                row.getString("work_key"),
                row.getString("base"),
                row.getString("notes"),
                row.getString("created_at"),
                row.getString("closed_at"));
    }

    private Map<String, List<ProjectName>> projectsOf(List<String> ids) {
        return grouped(store.sql("select task_id, project from task_project where task_id in (:ids) order by task_id, position")
                .param("ids", ids)
                .query((row, _) -> Map.entry(row.getString("task_id"), new ProjectName(row.getString("project"))))
                .list());
    }

    private Map<String, List<TaskId>> blockerIdsOf(List<String> ids) {
        return grouped(store.sql("select task_id, blocked_by from task_dependency where task_id in (:ids) order by task_id, rowid")
                .param("ids", ids)
                .query((row, _) -> Map.entry(row.getString("task_id"), new TaskId(row.getString("blocked_by"))))
                .list());
    }

    private Map<String, CaptainHold> holdsOf(List<String> ids) {
        return store.sql("select task_id, kind, reason, until, set_at from captain_hold where task_id in (:ids)")
                .param("ids", ids)
                .query((row, _) -> Map.entry(row.getString("task_id"), new CaptainHold(
                        HoldKind.named(row.getString("kind")),
                        row.getString("reason"),
                        Columns.holdUntil(row.getString("until")),
                        Columns.instant(row.getString("set_at")))))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static <T> Map<String, List<T>> grouped(List<Map.Entry<String, T>> entries) {
        return entries.stream().collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private record TaskParts(Map<String, List<ProjectName>> projects, Map<String, List<TaskId>> blockers, Map<String, CaptainHold> holds) {
    }

    private record TaskRow(
            String id,
            String kind,
            String title,
            String state,
            String workKey,
            String base,
            String notes,
            String created,
            String closed) {

        Task task(TaskParts parts) {
            Intake intake = new Intake(
                    TaskKind.named(kind),
                    title,
                    parts.projects().getOrDefault(id, List.of()),
                    workKey == null ? null : WorkKey.parse(workKey),
                    base == null ? null : new BaseBranch(base),
                    parts.blockers().getOrDefault(id, List.of()));
            return new Task(
                    new TaskId(id),
                    intake,
                    TaskState.named(state),
                    notes,
                    parts.holds().get(id),
                    Columns.instant(created),
                    Columns.instant(closed));
        }
    }
}
