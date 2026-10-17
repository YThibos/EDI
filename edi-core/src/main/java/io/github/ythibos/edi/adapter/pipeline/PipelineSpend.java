package io.github.ythibos.edi.adapter.pipeline;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.worker.TokenUsage;

final class PipelineSpend {

    private static final String SPEND_OF_A_BRANCH = """
            select count(*) as invocations,
                   count(invocation.delta_input_tokens) as counted,
                   coalesce(sum(invocation.delta_input_tokens), 0) as input,
                   coalesce(sum(invocation.delta_cache_read_tokens), 0) as cache_read,
                   coalesce(sum(invocation.cache_creation_tokens), 0) as cache_write,
                   coalesce(sum(invocation.delta_output_tokens), 0) as output
            from agent_invocations invocation
            join runs run on run.id = invocation.run_id
            join repos repository on repository.id = run.repo_id
            where repository.working_path = ? and run.branch = ?""";

    private PipelineSpend() {
    }

    static Optional<TokenUsage> recordedIn(Path stateDatabase, Path projectClone, BranchName branch) {
        if (!Files.isRegularFile(stateDatabase)) {
            return Optional.empty();
        }
        try (Connection database = PipelineState.readOnly(stateDatabase);
                PreparedStatement query = database.prepareStatement(SPEND_OF_A_BRANCH)) {
            PipelineState.bindTheBranch(query, projectClone, branch);
            return spendOf(query);
        } catch (SQLException unreadable) {
            return Optional.empty();
        }
    }

    private static Optional<TokenUsage> spendOf(PreparedStatement query) throws SQLException {
        try (ResultSet spend = query.executeQuery()) {
            return spend.next() && spend.getLong("invocations") == spend.getLong("counted") ? Optional.of(tokensOf(spend)) : Optional.empty();
        }
    }

    private static TokenUsage tokensOf(ResultSet spend) throws SQLException {
        return new TokenUsage(spend.getLong("input"), spend.getLong("cache_read"), spend.getLong("cache_write"), spend.getLong("output"));
    }
}
