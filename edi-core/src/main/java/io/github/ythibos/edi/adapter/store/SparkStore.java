package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.spark.Focus;
import io.github.ythibos.edi.domain.spark.Spark;
import io.github.ythibos.edi.domain.spark.SparkAuthor;
import io.github.ythibos.edi.domain.spark.SparkOutcome;
import io.github.ythibos.edi.domain.spark.SparkSorting;
import io.github.ythibos.edi.domain.spark.UnknownSpark;
import io.github.ythibos.edi.domain.task.TaskId;

@Component
public class SparkStore {

    private static final String COLUMNS = "number, text, author, session_id, focus_task, focus_project, caught_at, outcome, item_key, reason, sorted_at";

    private final JdbcClient store;

    public SparkStore(JdbcClient store) {
        this.store = store;
    }

    public int nextNumber() {
        return store.sql("select coalesce(max(number), 0) + 1 from spark").query((row, _) -> row.getInt(1)).single();
    }

    public void add(Spark spark) {
        store.sql("insert into spark (number, text, author, session_id, focus_task, focus_project, caught_at)"
                        + " values (:number, :text, :author, :session, :task, :project, :caught)")
                .param("number", spark.number())
                .param("text", spark.text())
                .param("author", spark.author().word())
                .param("session", Columns.textOf(spark.session()))
                .param("task", Columns.textOf(spark.focus().task()))
                .param("project", Columns.textOf(spark.focus().project()))
                .param("caught", Columns.text(spark.caughtAt()))
                .update();
    }

    public Spark get(int number) {
        return store.sql("select " + COLUMNS + " from spark where number = :number")
                .param("number", number)
                .query((row, _) -> spark(row))
                .optional()
                .orElseThrow(() -> new UnknownSpark(number));
    }

    public void sort(Spark spark) {
        SparkSorting sorting = spark.sorting();
        store.sql("update spark set outcome = :outcome, item_key = :item, reason = :reason, sorted_at = :sorted where number = :number")
                .param("outcome", sorting.outcome().word())
                .param("item", Columns.textOf(sorting.item()))
                .param("reason", sorting.reason())
                .param("sorted", Columns.text(sorting.sortedAt()))
                .param("number", spark.number())
                .update();
    }

    public List<Spark> open() {
        return store.sql("select " + COLUMNS + " from spark where outcome is null order by number").query((row, _) -> spark(row)).list();
    }

    private static Spark spark(ResultSet row) throws SQLException {
        String session = row.getString("session_id");
        return new Spark(row.getInt("number"), row.getString("text"), SparkAuthor.named(row.getString("author")),
                session == null ? null : UUID.fromString(session), focus(row), Columns.instant(row.getString("caught_at")), sorting(row));
    }

    private static Focus focus(ResultSet row) throws SQLException {
        String task = row.getString("focus_task");
        String project = row.getString("focus_project");
        return new Focus(task == null ? null : new TaskId(task), project == null ? null : new ProjectName(project));
    }

    private static SparkSorting sorting(ResultSet row) throws SQLException {
        String outcome = row.getString("outcome");
        if (outcome == null) {
            return null;
        }
        String item = row.getString("item_key");
        return new SparkSorting(SparkOutcome.named(outcome), item == null ? null : WorkKey.parse(item), row.getString("reason"),
                Columns.instant(row.getString("sorted_at")));
    }
}
