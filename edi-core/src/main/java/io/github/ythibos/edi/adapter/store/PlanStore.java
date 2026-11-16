package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.plan.Plan;
import io.github.ythibos.edi.domain.plan.PlanHeading;
import io.github.ythibos.edi.domain.plan.Slice;
import io.github.ythibos.edi.domain.plan.SliceWait;
import io.github.ythibos.edi.domain.plan.UnknownPlan;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.AwaitedStage;
import io.github.ythibos.edi.domain.task.TaskId;

@Component
public class PlanStore {

    private final JdbcClient store;

    public PlanStore(JdbcClient store) {
        this.store = store;
    }

    public boolean contains(WorkKey key) {
        return store.sql("select count(*) from plan where work_key = :key")
                .param("key", key.toString())
                .query((row, _) -> row.getInt(1))
                .single() > 0;
    }

    public void insert(Plan plan, Instant addedAt) {
        insertHeading(plan.heading(), addedAt);
        plan.slices().forEach(slice -> insertSlice(plan.key(), slice));
    }

    public Plan get(WorkKey key) {
        PlanHeading heading = store.sql("select work_key, title, project, report, go, capacity, effort from plan where work_key = :key")
                .param("key", key.toString())
                .query((row, _) -> heading(row))
                .optional()
                .orElseThrow(() -> new UnknownPlan(key));
        return new Plan(heading, slicesOf(key));
    }

    private void insertHeading(PlanHeading heading, Instant addedAt) {
        store.sql("insert into plan (work_key, title, project, report, go, capacity, effort, added_at)"
                        + " values (:key, :title, :project, :report, :go, :capacity, :effort, :addedAt)")
                .param("key", heading.key().toString())
                .param("title", heading.title())
                .param("project", heading.project().value())
                .param("report", heading.report())
                .param("go", heading.go())
                .param("capacity", heading.capacity())
                .param("effort", heading.effort() == null ? null : heading.effort().word())
                .param("addedAt", Columns.text(addedAt))
                .update();
    }

    private void insertSlice(WorkKey plan, Slice slice) {
        SliceWait.After after = slice.awaits() instanceof SliceWait.After waitsAfter ? waitsAfter : null;
        store.sql("insert into plan_slice (plan_key, number, task_id, category, waits_after, waits_until)"
                        + " values (:plan, :number, :task, :category, :after, :until)")
                .param("plan", plan.toString())
                .param("number", slice.number())
                .param("task", slice.task().value())
                .param("category", slice.category().value())
                .param("after", after == null ? null : after.slice())
                .param("until", after == null ? null : after.stage().word())
                .update();
    }

    private List<Slice> slicesOf(WorkKey plan) {
        return store.sql("select number, task_id, category, waits_after, waits_until from plan_slice where plan_key = :plan order by number")
                .param("plan", plan.toString())
                .query((row, _) -> slice(row))
                .list();
    }

    private static PlanHeading heading(ResultSet row) throws SQLException {
        String effort = row.getString("effort");
        return new PlanHeading(WorkKey.parse(row.getString("work_key")), row.getString("title"), new ProjectName(row.getString("project")),
                row.getString("report"), row.getString("go"), row.getInt("capacity"), effort == null ? null : EffortLevel.named(effort));
    }

    private static Slice slice(ResultSet row) throws SQLException {
        return new Slice(row.getInt("number"), new TaskId(row.getString("task_id")), new CategoryId(row.getString("category")), waitOf(row));
    }

    private static SliceWait waitOf(ResultSet row) throws SQLException {
        String until = row.getString("waits_until");
        return until == null ? new SliceWait.None() : new SliceWait.After(row.getInt("waits_after"), AwaitedStage.named(until));
    }
}
