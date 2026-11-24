package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.decision.UnknownDecision;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.AwaitedStage;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.Hold;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.Holds;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskStage;
import io.github.ythibos.edi.domain.task.TaskSize;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.domain.task.UnknownTask;
import io.github.ythibos.edi.domain.task.Wait;

@Component
public class TaskStore {

    private static final String SELECT_TASKS = "select id, kind, title, stage, work_key, base, delivery, size, notes, created_at, closed_at from task ";

    private final JdbcClient store;

    public TaskStore(JdbcClient store) {
        this.store = store;
    }

    public boolean contains(TaskId id) {
        return store.sql("select count(*) from task where id = :id")
                .param("id", id.value())
                .query((row, _) -> row.getInt(1))
                .single() > 0;
    }

    public List<TaskId> ids() {
        return store.sql("select id from task order by rowid").query((row, _) -> new TaskId(row.getString("id"))).list();
    }

    public TaskId firstFreeIdFrom(TaskId slug) {
        TaskId candidate = slug;
        for (int suffix = 2; contains(candidate); suffix++) {
            candidate = slug.withSuffix(suffix);
        }
        return candidate;
    }

    public void insert(Task task) {
        store.sql("""
                        insert into task (id, kind, title, stage, work_key, base, delivery, size, notes, created_at, closed_at)
                        values (:id, :kind, :title, :stage, :workKey, :base, :delivery, :size, :notes, :created, :closed)""")
                .param("id", task.id().value())
                .param("kind", task.intake().kind().word())
                .param("title", task.intake().title())
                .param("stage", task.stage().word())
                .param("workKey", Columns.textOf(task.intake().workKey()))
                .param("base", Columns.textOf(task.intake().base()))
                .param("delivery", task.intake().delivery() == null ? null : task.intake().delivery().word())
                .param("size", task.intake().size() == null ? null : task.intake().size().word())
                .param("notes", task.notes())
                .param("created", Columns.text(task.created()))
                .param("closed", Columns.text(task.closed()))
                .update();
        insertProjects(task);
        insertBlockers(task);
        insertHolds(task);
    }

    public void update(Task task) {
        store.sql("update task set stage = :stage, work_key = :workKey, notes = :notes, closed_at = :closed where id = :id")
                .param("id", task.id().value())
                .param("stage", task.stage().word())
                .param("workKey", Columns.textOf(task.intake().workKey()))
                .param("notes", task.notes())
                .param("closed", Columns.text(task.closed()))
                .update();
        replaceBlockers(task);
        replaceHolds(task);
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
        return tasks(store.sql(SELECT_TASKS + "where stage in (:stages) order by rowid").param("stages", stageWordsOf(state)));
    }

    public List<Task> closedSince(TaskState state, Instant since) {
        return tasks(store.sql(SELECT_TASKS + "where stage in (:stages) and closed_at >= :since order by closed_at desc, rowid desc")
                .param("stages", stageWordsOf(state))
                .param("since", Columns.text(since)));
    }

    public List<Task> keyedOn(WorkKey key) {
        return tasks(store.sql(SELECT_TASKS + "where work_key = :key order by rowid").param("key", key.toString()));
    }

    public List<Task> waitingFor(TaskId blocker) {
        return tasks(store.sql(SELECT_TASKS + "where id in (select task_id from task_dependency where blocked_by = :blocker) order by rowid")
                .param("blocker", blocker.value()));
    }

    public List<Wait> waitsOf(Task task) {
        return Stream.concat(waitsForBlockersOf(task).stream(), waitInItsPlan(task.id()).stream()).toList();
    }

    private List<Wait> waitsForBlockersOf(Task task) {
        List<TaskId> blockedBy = task.intake().blockedBy();
        if (blockedBy.isEmpty()) {
            return List.of();
        }
        Map<String, TaskStage> stages = store.sql("select id, stage from task where id in (:ids)")
                .param("ids", blockedBy.stream().map(TaskId::value).toList())
                .query((row, _) -> Map.entry(row.getString("id"), TaskStage.named(row.getString("stage"))))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return blockedBy.stream().map(blocker -> Wait.afterLanded(blocker, stages.get(blocker.value()))).toList();
    }

    private Optional<Wait> waitInItsPlan(TaskId slice) {
        return store.sql("select awaited.task_id, waiting.waits_until, task.stage from plan_slice waiting"
                        + " join plan_slice awaited on awaited.plan_key = waiting.plan_key and awaited.number = waiting.waits_after"
                        + " join task on task.id = awaited.task_id"
                        + " where waiting.task_id = :slice")
                .param("slice", slice.value())
                .query((row, _) -> new Wait(new TaskId(row.getString("task_id")), AwaitedStage.named(row.getString("waits_until")),
                        TaskStage.named(row.getString("stage"))))
                .optional();
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

    private void replaceBlockers(Task task) {
        store.sql("delete from task_dependency where task_id = :task").param("task", task.id().value()).update();
        insertBlockers(task);
    }

    private void insertBlockers(Task task) {
        task.intake().blockedBy().forEach(blocker ->
                store.sql("insert into task_dependency (task_id, blocked_by) values (:task, :blocker)")
                        .param("task", task.id().value())
                        .param("blocker", blocker.value())
                        .update());
    }

    private void insertHolds(Task task) {
        task.holds().held().forEach(hold ->
                store.sql("insert into task_hold (task_id, kind, decision, outage, why, until, set_at) values (:task, :kind, :decision, :outage, :why, :until, :since)")
                        .param("task", task.id().value())
                        .params(HoldColumns.of(hold).asParameters())
                        .update());
    }

    private void replaceHolds(Task task) {
        store.sql("delete from task_hold where task_id = :task").param("task", task.id().value()).update();
        insertHolds(task);
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
                row.getString("stage"),
                row.getString("work_key"),
                row.getString("base"),
                row.getString("delivery"),
                row.getString("size"),
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

    private Map<String, List<Hold>> holdsOf(List<String> ids) {
        return grouped(store.sql("""
                        select task_hold.task_id, kind, decision, outage, coalesce(why, raised.title) as why, until, set_at
                        from task_hold left join decision raised on raised.number = task_hold.decision
                        where task_hold.task_id in (:ids) order by task_hold.task_id, task_hold.rowid""")
                .param("ids", ids)
                .query((row, _) -> Map.entry(row.getString("task_id"), HoldColumns.read(row).hold()))
                .list());
    }

    private static <T> Map<String, List<T>> grouped(List<Map.Entry<String, T>> entries) {
        return entries.stream().collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private static List<String> stageWordsOf(TaskState state) {
        return Arrays.stream(TaskStage.values()).filter(stage -> stage.state() == state).map(TaskStage::word).toList();
    }

    private record TaskParts(Map<String, List<ProjectName>> projects, Map<String, List<TaskId>> blockers, Map<String, List<Hold>> holds) {
    }

    private record TaskRow(
            String id,
            String kind,
            String title,
            String stage,
            String workKey,
            String base,
            String delivery,
            String size,
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
                    parts.blockers().getOrDefault(id, List.of()),
                    delivery == null ? null : DeliveryMode.named(delivery),
                    size == null ? null : TaskSize.named(size));
            return new Task(
                    new TaskId(id),
                    intake,
                    TaskStage.named(stage),
                    notes,
                    new Holds(parts.holds().getOrDefault(id, List.of())),
                    Columns.instant(created),
                    Columns.instant(closed));
        }
    }

    private record HoldColumns(String kind, Integer decision, Integer outage, String why, String until, String since) {

        static HoldColumns of(Hold hold) {
            return switch (hold) {
                case Hold.OpenDecision decision -> new HoldColumns(hold, decision.number().value(), null, null);
                case Hold.Outage outage -> new HoldColumns(hold, null, outage.entry(), outage.lostWhen());
                case Hold.Deferral deferral -> new HoldColumns(hold, null, null, deferral.why());
                case Hold.Approval approval -> new HoldColumns(hold, null, null, approval.why());
            };
        }

        static HoldColumns read(ResultSet row) throws SQLException {
            return new HoldColumns(
                    row.getString("kind"),
                    numberIn(row, "decision"),
                    numberIn(row, "outage"),
                    row.getString("why"),
                    row.getString("until"),
                    row.getString("set_at"));
        }

        private HoldColumns(Hold hold, Integer decision, Integer outage, String why) {
            this(hold.kind().word(), decision, outage, why, Columns.text(hold.until()), Columns.text(hold.since()));
        }

        private static Integer numberIn(ResultSet row, String column) throws SQLException {
            int number = row.getInt(column);
            return row.wasNull() ? null : number;
        }

        Map<String, Object> asParameters() {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("kind", kind);
            parameters.put("decision", decision);
            parameters.put("outage", outage);
            parameters.put("why", why);
            parameters.put("until", until);
            parameters.put("since", since);
            return parameters;
        }

        Hold hold() {
            Instant at = Columns.instant(since);
            return switch (HoldKind.named(kind)) {
                case DECISION -> new Hold.OpenDecision(new DecisionNumber(decision), why, at);
                case OUTAGE -> new Hold.Outage(outage, why, at);
                case DEFERRAL -> new Hold.Deferral(why, Columns.holdUntil(until), at);
                case APPROVAL -> new Hold.Approval(why, Columns.holdUntil(until), at);
            };
        }
    }
}
