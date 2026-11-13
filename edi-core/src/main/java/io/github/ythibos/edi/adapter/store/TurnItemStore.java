package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.report.TaskWording;
import io.github.ythibos.edi.domain.report.Turn;
import io.github.ythibos.edi.domain.report.TurnItem;

@Component
public class TurnItemStore {

    private static final String DONE = "done";
    private static final String NEXT = "next";
    private static final String TASK_WORDING = "task-wording";
    private static final String PROJECT_SEPARATOR = "\n";

    private final JdbcClient store;

    public TurnItemStore(JdbcClient store) {
        this.store = store;
    }

    public Turn recordedIn(UUID session) {
        List<StoredItem> rows = store.sql("select part, task_id, text, state, projects from turn_item where session_id = :session order by position")
                .param("session", session.toString())
                .query((row, _) -> storedItem(row))
                .list();
        return new Turn(itemsOf(rows, DONE), itemsOf(rows, NEXT), wordingsOf(rows));
    }

    public void replace(UUID session, Turn items) {
        clear(session);
        List<StoredItem> rows = new ArrayList<>();
        items.done().forEach(item -> rows.add(StoredItem.of(DONE, item)));
        items.next().forEach(item -> rows.add(StoredItem.of(NEXT, item)));
        items.tasks().forEach((task, wording) -> rows.add(StoredItem.of(task, wording)));
        for (int position = 0; position < rows.size(); position++) {
            insert(session, position, rows.get(position));
        }
    }

    public void clear(UUID session) {
        store.sql("delete from turn_item where session_id = :session").param("session", session.toString()).update();
    }

    private static StoredItem storedItem(ResultSet row) throws SQLException {
        return new StoredItem(row.getString("part"), row.getString("task_id"), row.getString("text"), row.getString("state"),
                projectsIn(row.getString("projects")));
    }

    private static List<TurnItem> itemsOf(List<StoredItem> rows, String part) {
        return rows.stream().filter(row -> row.part().equals(part)).map(row -> new TurnItem(row.projects(), row.text())).toList();
    }

    private static Map<String, TaskWording> wordingsOf(List<StoredItem> rows) {
        Map<String, TaskWording> wordings = new LinkedHashMap<>();
        rows.stream().filter(row -> row.part().equals(TASK_WORDING))
                .forEach(row -> wordings.put(row.task(), new TaskWording(row.text(), row.state(), row.projects())));
        return wordings;
    }

    private void insert(UUID session, int position, StoredItem item) {
        store.sql("""
                        insert into turn_item (session_id, position, part, task_id, text, state, projects)
                        values (:session, :position, :part, :task, :text, :state, :projects)""")
                .param("session", session.toString())
                .param("position", position)
                .param("part", item.part())
                .param("task", item.task())
                .param("text", item.text())
                .param("state", item.state())
                .param("projects", projectsColumn(item.projects()))
                .update();
    }

    private static List<String> projectsIn(String column) {
        if (column == null) {
            return null;
        }
        return column.isEmpty() ? List.of() : Arrays.asList(column.split(PROJECT_SEPARATOR));
    }

    private static String projectsColumn(List<String> projects) {
        return projects == null ? null : String.join(PROJECT_SEPARATOR, projects);
    }

    private record StoredItem(String part, String task, String text, String state, List<String> projects) {

        static StoredItem of(String part, TurnItem item) {
            return new StoredItem(part, null, item.text(), null, item.projects());
        }

        static StoredItem of(String task, TaskWording wording) {
            return new StoredItem(TASK_WORDING, task, wording.text(), wording.state(), wording.projects());
        }
    }
}
