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
import io.github.ythibos.edi.domain.delivery.PipelineReview;
import tools.jackson.databind.json.JsonMapper;

class PipelineReviewsTest {

    private static final BranchName BRANCH = BranchName.parse("feature/HRN-3");
    private static final JsonMapper JSON = new JsonMapper();

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
                create table step_results (id text primary key, run_id text not null, step_name text not null);
                create table step_rounds (id text primary key, step_result_id text not null, round integer not null, findings_json text);
                insert into repos values ('repo', '%s'), ('other', '/elsewhere');
                insert into runs values ('run-1', 'repo', 'feature/HRN-3'), ('run-2', 'repo', 'feature/HRN-3'), ('run-3', 'other', 'feature/HRN-3');
                insert into step_results values ('r1', 'run-1', 'review'), ('t1', 'run-1', 'test'), ('r2', 'run-2', 'review'), ('r3', 'run-3', 'review');
                insert into step_rounds values
                    ('a', 'r1', 1, '{"findings":[{"id":"review-1"},{"id":"review-2"}]}'),
                    ('b', 'r1', 2, '{"findings":[{"id":"review-1"}]}'),
                    ('c', 't1', 1, '{"findings":[{"id":"test-1"}]}'),
                    ('d', 'r2', 1, '{"findings":null}'),
                    ('e', 'r3', 1, '{"findings":[{"id":"x"},{"id":"y"},{"id":"z"}]}');
                """.formatted(clone.toRealPath()));
    }

    @Test
    void countsTheReviewRoundsAndTheFindingsOfEachFirstReview() {
        assertThat(PipelineReviews.recordedIn(state, clone, BRANCH, JSON)).contains(new PipelineReview(3, 2));
    }

    @Test
    void keepsTheReviewUnknownWhenThePipelineNeverReviewedTheBranchOrItsStateCannotBeRead() {
        assertThat(PipelineReviews.recordedIn(state, clone, BranchName.parse("feature/HRN-9"), JSON)).isEmpty();
        assertThat(PipelineReviews.recordedIn(directory.resolve("absent.sqlite"), clone, BRANCH, JSON)).isEmpty();
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
