package io.github.ythibos.edi.adapter.pipeline;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.delivery.PipelineReview;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

final class PipelineReviews {

    private static final String REVIEW_ROUNDS_OF_A_BRANCH = """
            select review.round, review.findings_json
            from step_rounds review
            join step_results step on step.id = review.step_result_id
            join runs run on run.id = step.run_id
            join repos repository on repository.id = run.repo_id
            where repository.working_path = ? and run.branch = ? and step.step_name = 'review'""";
    private static final int FIRST_ROUND = 1;

    private PipelineReviews() {
    }

    static Optional<PipelineReview> recordedIn(Path stateDatabase, Path projectClone, BranchName branch, JsonMapper json) {
        if (!Files.isRegularFile(stateDatabase)) {
            return Optional.empty();
        }
        try (Connection database = PipelineState.readOnly(stateDatabase);
                PreparedStatement query = database.prepareStatement(REVIEW_ROUNDS_OF_A_BRANCH)) {
            PipelineState.bindTheBranch(query, projectClone, branch);
            return reviewOf(query, json);
        } catch (SQLException | JacksonException unreadable) {
            return Optional.empty();
        }
    }

    private static Optional<PipelineReview> reviewOf(PreparedStatement query, JsonMapper json) throws SQLException {
        int rounds = 0;
        int findings = 0;
        try (ResultSet review = query.executeQuery()) {
            while (review.next()) {
                rounds++;
                findings += review.getInt("round") == FIRST_ROUND ? findingsIn(review.getString("findings_json"), json) : 0;
            }
        }
        return rounds == 0 ? Optional.empty() : Optional.of(new PipelineReview(rounds, findings));
    }

    private static int findingsIn(String findingsJson, JsonMapper json) {
        return findingsJson == null ? 0 : json.readTree(findingsJson).path("findings").size();
    }
}
