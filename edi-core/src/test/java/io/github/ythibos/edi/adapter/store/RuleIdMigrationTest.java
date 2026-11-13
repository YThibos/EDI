package io.github.ythibos.edi.adapter.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.ythibos.edi.adapter.store.RuleFirings.FiringCount;
import io.github.ythibos.edi.domain.rule.Rule;

class RuleIdMigrationTest {

    private static final String FIRED = "2026-11-12T09:00:00Z";

    @TempDir
    private Path directory;

    private JdbcClient jdbc;
    private DataSource store;

    @BeforeEach
    void storeFiringsBeforePlans() throws IOException {
        store = StoreFile.openedAt(directory.resolve("edi.db"));
        Flyway.configure().dataSource(store).target("28").load().migrate();
        jdbc = JdbcClient.create(store);
        firing("dispatch.blockers-done", "fit-lamp waits for sail-away to land; it is dispatched once each wait is met.");
        firing("guard.merge", "only the captain merges.");
    }

    @Test
    void readsAFiringRecordedBeforeTheRenameAsTheRuleThatEveryWaitIsMet() {
        Flyway.configure().dataSource(store).load().migrate();

        assertThat(new RuleFirings(jdbc).countedSince(Instant.parse(FIRED))).extracting(FiringCount::rule, FiringCount::fired)
                .containsExactly(tuple("dispatch.waits-met", 1L), tuple("guard.merge", 1L));
        assertThat(Rule.withId("dispatch.waits-met")).contains(Rule.WAITS_MET);
    }

    private void firing(String rule, String reason) {
        jdbc.sql("insert into rule_fired (at, rule, outcome, actor, reason) values (:at, :rule, 'refused', 'primary', :reason)")
                .param("at", FIRED).param("rule", rule).param("reason", reason).update();
    }
}
