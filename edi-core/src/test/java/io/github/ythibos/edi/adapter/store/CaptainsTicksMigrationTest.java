package io.github.ythibos.edi.adapter.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.ythibos.edi.domain.call.CallKey;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;

class CaptainsTicksMigrationTest {

    private static final String BEFORE = "2026-11-20T09:00:00Z";

    @TempDir
    private Path directory;

    @Test
    void ticksEverythingDealtWithBeforeTheMigrationSoOnlyWhatIsDealtWithAfterItWaitsForATick() throws IOException {
        DataSource store = StoreFile.openedAt(directory.resolve("edi.db"));
        Flyway.configure().dataSource(store).target("33").load().migrate();
        JdbcClient jdbc = JdbcClient.create(store);
        storeWhatTheCaptainDealtWith(jdbc);

        Flyway.configure().dataSource(store).load().migrate();
        DecisionStore decisions = new DecisionStore(jdbc);

        assertThat(decisions.answeredAwaitingTheirTick()).isEmpty();
        assertThat(new BoardStore(jdbc).endedAwaitingTheirTick()).isEmpty();
        assertThat(new DeliveryStore(jdbc).settledAwaitingTheirTick()).isEmpty();

        decisions.save(decisions.get(new DecisionNumber(59)).answered(
                new CaptainAnswer("A", new OptionKey('A'), "", Instant.parse("2026-11-25T09:00:00Z"), AnswerChannel.CONSOLE)));

        assertThat(decisions.answeredAwaitingTheirTick()).extracting(decision -> decision.number().toString()).containsExactly("D59");

        new CallTickStore(jdbc).tick(new CallKey.OfADecision(new DecisionNumber(59)), null, Instant.parse("2026-11-25T09:05:00Z"));

        assertThat(decisions.answeredAwaitingTheirTick()).isEmpty();
    }

    private static void storeWhatTheCaptainDealtWith(JdbcClient jdbc) {
        jdbc.sql("insert into task (id, kind, title, stage, notes, created_at) values ('fit-lamp', 'ship', 'Fit the lamp', 'working', '', :at)")
                .param("at", BEFORE).update();
        jdbc.sql("""
                        insert into decision (number, task_id, title, context, recommended, recommended_because, raised_at,
                                              answer_verbatim, answer_option, answered_at, answer_channel)
                        values (58, 'fit-lamp', 'Which lamp?', '', 'A', 'it is enough', :at, 'A', 'A', :at, 'console')""")
                .param("at", BEFORE).update();
        jdbc.sql("""
                        insert into decision (number, task_id, title, context, recommended, recommended_because, raised_at)
                        values (59, 'fit-lamp', 'Which bulb?', '', 'A', 'it is enough', :at)""")
                .param("at", BEFORE).update();
        jdbc.sql("insert into decision_option (decision_number, position, option_key, option_text) values (59, 0, 'A', 'The warm one')").update();
        jdbc.sql("insert into board (task_id, file, rounds, armed_at, ended_at) values ('fit-lamp', '/tmp/board.html', 1, :at, :at)")
                .param("at", BEFORE).update();
        jdbc.sql("""
                        insert into delivery (task_id, provider, host, path, number, url, draft, state, landed_commit, recorded_at, observed_at)
                        values ('fit-lamp', 'github', 'github.com', 'example-owner/harness', 7, 'https://github.com/example-owner/harness/pull/7', 0,
                                'merged', 'def456', :at, :at)""")
                .param("at", BEFORE).update();
    }
}
