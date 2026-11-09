package io.github.ythibos.edi.adapter.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

class PassedChecksTest {

    @TempDir
    private Path directory;

    @Test
    void readsTheChecksOfARunAsPassedOnlyWhileThePipelineRecordsThemReady() throws SQLException {
        Path state = stateHolding("""
                create table runs (id text primary key, ci_ready_at integer);
                insert into runs values ('watching', 1794241835), ('fixing', null);
                """);

        assertThat(PassedChecks.recordedIn(state, "watching")).isTrue();
        assertThat(PassedChecks.recordedIn(state, "fixing")).isFalse();
        assertThat(PassedChecks.recordedIn(state, "unknown")).isFalse();
    }

    @Test
    void readsNoPassedChecksFromAStoreThatNeverRecordsThemOrIsMissing() throws SQLException {
        Path older = stateHolding("create table runs (id text primary key); insert into runs values ('watching');");

        assertThat(PassedChecks.recordedIn(older, "watching")).isFalse();
        assertThat(PassedChecks.recordedIn(directory.resolve("absent.sqlite"), "watching")).isFalse();
    }

    private Path stateHolding(String statements) throws SQLException {
        Path state = directory.resolve("state.sqlite");
        SQLiteDataSource database = new SQLiteDataSource();
        database.setUrl("jdbc:sqlite:" + state);
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : statements.split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
        return state;
    }
}
