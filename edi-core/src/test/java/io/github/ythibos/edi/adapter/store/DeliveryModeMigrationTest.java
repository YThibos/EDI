package io.github.ythibos.edi.adapter.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.task.TaskId;

class DeliveryModeMigrationTest {

    private static final String CREATED = "2026-10-22T09:00:00Z";

    @TempDir
    private Path directory;

    @Test
    void keepsEveryTasksDeliveryAndLetsATaskBeDeliveredByItsReport() throws IOException {
        DataSource store = StoreFile.openedAt(directory.resolve("edi.db"));
        Flyway.configure().dataSource(store).target("13").load().migrate();
        JdbcClient jdbc = JdbcClient.create(store);
        jdbc.sql("""
                        insert into task (id, kind, title, state, notes, created_at, delivery)
                        values ('fit-lamp', 'ship', 'Fit the lamp', 'queued', '', :at, 'local-apply'),
                               ('fit-horn', 'ship', 'Fit the horn', 'queued', '', :at, null)""")
                .param("at", CREATED).update();

        Flyway.configure().dataSource(store).load().migrate();
        jdbc.sql("""
                        insert into task (id, kind, title, stage, notes, created_at, delivery)
                        values ('move-shelf', 'ship', 'Move the shelf', 'queued', '', :at, 'report')""")
                .param("at", CREATED).update();
        TaskStore tasks = new TaskStore(jdbc);

        assertThat(tasks.get(new TaskId("fit-lamp")).intake().delivery()).isEqualTo(DeliveryMode.LOCAL_APPLY);
        assertThat(tasks.get(new TaskId("fit-horn")).intake().delivery()).isNull();
        assertThat(tasks.get(new TaskId("move-shelf")).intake().delivery()).isEqualTo(DeliveryMode.REPORT);
        assertThat(jdbc.sql("pragma foreign_key_check").query((row, _) -> row.getString(1)).list()).isEqualTo(List.of());
    }
}
