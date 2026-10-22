package io.github.ythibos.edi.adapter.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.testing.Cards;

class DecisionsMigrationTest {

    private static final String RAISED = "2026-10-20T09:00:00Z";
    private static final TaskId LAMP = new TaskId("choose-lamp");

    @TempDir
    private Path directory;

    @Test
    void keepsEveryAnsweredDecisionWithItsOptionsAndLetsItsTaskHoldANewOne() throws IOException {
        DataSource store = StoreFile.openedAt(directory.resolve("edi.db"));
        Flyway.configure().dataSource(store).target("10").load().migrate();
        JdbcClient jdbc = JdbcClient.create(store);
        jdbc.sql("insert into task (id, kind, title, state, notes, created_at) values ('choose-lamp', 'captain', 'Choose the lamp', 'queued', '', :at)")
                .param("at", RAISED).update();
        jdbc.sql("""
                        insert into decision (number, task_id, title, context, recommended, recommended_because, raised_at, answer_verbatim,
                                              answer_option, answer_interpretation, answered_at, answer_channel)
                        values (58, 'choose-lamp', 'Which lamp?', '', 'A', 'it is enough', :at, 'D58 A', 'A', '', :at, 'board')""")
                .param("at", RAISED).update();
        jdbc.sql("""
                        insert into decision_option (decision_number, position, option_key, option_text, authorises)
                        values (58, 0, 'A', 'The simple way', 'promote'), (58, 1, 'B', 'The thorough way', null)""").update();

        Flyway.configure().dataSource(store).load().migrate();
        DecisionStore decisions = new DecisionStore(jdbc);
        Decision answered = decisions.get(new DecisionNumber(58));
        Decision next = Decision.raised(new DecisionNumber(59), LAMP, Cards.card("Which lamp now?"), Instant.parse(RAISED).plusSeconds(60));
        decisions.save(next);

        assertThat(answered.answer().verbatim()).isEqualTo("D58 A");
        assertThat(answered.card().options()).extracting(option -> option.key().toString()).containsExactly("A", "B");
        assertThat(decisions.everyOneOfTask(LAMP)).extracting(Decision::number).containsExactly(new DecisionNumber(58), new DecisionNumber(59));
        assertThat(jdbc.sql("pragma foreign_key_check").query((row, _) -> row.getString(1)).list()).isEqualTo(List.of());
    }
}
