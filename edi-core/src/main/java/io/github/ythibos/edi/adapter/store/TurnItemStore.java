package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        return store.sql("select part, task_id, text, state, projects from turn_item where session_id = :session order by position")
                .param("session", session.toString())
                .query((row, _) -> storedItem(row))
                .list().stream()
                .map(StoredItem::asTurn)
                .reduce(Turn.NONE, Turn::followedBy);
    }

    public Optional<Instant> lastRecordedIn(UUID session) {
        return store.sql("select recorded_at from turn_item where session_id = :session")
                .param("session", session.toString())
                .query((row, _) -> Columns.instant(row.getString("recorded_at")))
                .list().stream()
                .max(Comparator.naturalOrder());
    }

    public void add(UUID session, Turn items, Instant recordedAt) {
        List<StoredItem> rows = new ArrayList<>();
        items.done().forEach(item -> rows.add(StoredItem.of(DONE, item)));
        items.next().forEach(item -> rows.add(StoredItem.of(NEXT, item)));
        items.tasks().forEach((task, wording) -> rows.add(StoredItem.of(task, wording)));
        int first = nextPositionIn(session);
        for (int offset = 0; offset < rows.size(); offset++) {
            insert(session, first + offset, rows.get(offset), recordedAt);
        }
    }

    public void clear(UUID session) {
        store.sql("delete from turn_item where session_id = :session").param("session", session.toString()).update();
    }

    private static StoredItem storedItem(ResultSet row) throws SQLException {
        return new StoredItem(row.getString("part"), row.getString("task_id"), row.getString("text"), row.getString("state"),
                projectsIn(row.getString("projects")));
    }

    private int nextPositionIn(UUID session) {
        return store.sql("select coalesce(max(position) + 1, 0) from turn_item where session_id = :session")
                .param("session", session.toString())
                .query(Integer.class)
                .single();
    }

    private void insert(UUID session, int position, StoredItem item, Instant recordedAt) {
        store.sql("""
                        insert into turn_item (session_id, position, part, task_id, text, state, projects, recorded_at)
                        values (:session, :position, :part, :task, :text, :state, :projects, :recordedAt)""")
                .param("session", session.toString())
                .param("position", position)
                .param("part", item.part())
                .param("task", item.task())
                .param("text", item.text())
                .param("state", item.state())
                .param("projects", projectsColumn(item.projects()))
                .param("recordedAt", Columns.text(recordedAt))
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

        Turn asTurn() {
            return switch (part) {
                case DONE -> new Turn(List.of(new TurnItem(projects, text)), List.of(), Map.of());
                case NEXT -> new Turn(List.of(), List.of(new TurnItem(projects, text)), Map.of());
                default -> new Turn(List.of(), List.of(), Map.of(task, new TaskWording(text, state, projects)));
            };
        }
    }
}
