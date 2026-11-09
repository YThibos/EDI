package io.github.ythibos.edi.adapter.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.worker.TokenUsage;

class PipelineSpendTest {

    private static final BranchName BRANCH = BranchName.parse("feature/HRN-3");

    @TempDir
    private Path directory;

    private Path state;
    private Path clone;

    @BeforeEach
    void createThePipelineState() throws IOException, SQLException {
        state = directory.resolve("state.sqlite");
        clone = Files.createDirectories(directory.resolve("projects/harness"));
        execute("""
                create table repos (id text primary key, working_path text not null unique);
                create table runs (id text primary key, repo_id text not null, branch text not null);
                create table agent_invocations (id text primary key, run_id text not null, step_name text not null, round integer not null,
                    exit_status text not null, input_tokens integer, output_tokens integer, cache_read_tokens integer, cache_creation_tokens integer,
                    delta_input_tokens integer, delta_output_tokens integer, delta_cache_read_tokens integer);
                insert into repos values ('repo', '%s'), ('other', '/elsewhere');
                insert into runs values ('run-1', 'repo', 'feature/HRN-3'), ('run-2', 'repo', 'feature/HRN-3'), ('run-3', 'repo', 'feature/HRN-4'),
                    ('run-4', 'other', 'feature/HRN-3');
                insert into agent_invocations values
                    ('a', 'run-1', 'review', 1, 'ok', 100, 10, 1000, 50, 100, 10, 1000),
                    ('b', 'run-1', 'review', 2, 'failed', 20, 2, 200, 5, 20, 2, 200),
                    ('c', 'run-2', 'ci', 1, 'ok', 3, 1, 30, 7, 3, 1, 30),
                    ('d', 'run-3', 'ci', 1, 'ok', 9000, 9000, 9000, 9000, 9000, 9000, 9000),
                    ('e', 'run-4', 'ci', 1, 'ok', 9000, 9000, 9000, 9000, 9000, 9000, 9000);
                """.formatted(clone.toRealPath()));
    }

    @Test
    void sumsTheRoundsOfEveryRunOnTheBranchOfTheCloneInAStoreFromBeforeItHadFreshInput() {
        assertThat(PipelineSpend.recordedIn(state, clone, BRANCH)).contains(new TokenUsage(123, 1230, 62, 13));
    }

    @Test
    void readsTheInputOfAStoreWhoseInputHoldsItsCacheReadsAndWritesFromItsFreshInputLessItsCacheWrites() throws SQLException {
        execute("""
                alter table agent_invocations add column fresh_input_tokens integer;
                insert into runs values ('run-5', 'repo', 'feature/HRN-5');
                insert into agent_invocations values ('g', 'run-5', 'review', 1, 'ok', 905040, 700, 900000, 5000, 905040, 700, 900000, 5040);
                """);

        assertThat(PipelineSpend.recordedIn(state, clone, BranchName.parse("feature/HRN-5"))).contains(new TokenUsage(40, 900000, 5000, 700));
    }

    @Test
    void countsEachRoundOfABranchByTheAccountingItWasRecordedWith() throws SQLException {
        execute("""
                alter table agent_invocations add column fresh_input_tokens integer;
                insert into runs values ('run-5', 'repo', 'feature/HRN-3');
                insert into agent_invocations values ('g', 'run-5', 'review', 1, 'ok', 905040, 700, 900000, 5000, 905040, 700, 900000, 5040);
                """);

        assertThat(PipelineSpend.recordedIn(state, clone, BRANCH)).contains(new TokenUsage(163, 901230, 5062, 713));
    }

    @Test
    void keepsTheSpendUnknownWhenARoundHasNoCountsOrTheStateCannotBeRead() throws SQLException {
        assertThat(PipelineSpend.recordedIn(directory.resolve("absent.sqlite"), clone, BRANCH)).isEmpty();

        execute("insert into agent_invocations (id, run_id, step_name, round, exit_status) values ('f', 'run-2', 'test', 1, 'cancelled');");

        assertThat(PipelineSpend.recordedIn(state, clone, BRANCH)).isEmpty();
    }

    @Test
    void countsNothingSpentOnABranchThePipelineNeverRan() {
        assertThat(PipelineSpend.recordedIn(state, clone, BranchName.parse("feature/HRN-9"))).contains(TokenUsage.NONE);
    }

    private void execute(String statements) throws SQLException {
        SQLiteDataSource database = new SQLiteDataSource();
        database.setUrl("jdbc:sqlite:" + state);
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : statements.split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
    }
}
