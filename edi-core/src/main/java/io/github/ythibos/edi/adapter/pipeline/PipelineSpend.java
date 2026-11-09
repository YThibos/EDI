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

    private static final String INVOCATIONS_ON_A_BRANCH = """
            select invocation.delta_input_tokens as input,
                   invocation.delta_cache_read_tokens as cache_read,
                   invocation.cache_creation_tokens as cache_write,
                   invocation.delta_output_tokens as output
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
                PreparedStatement query = database.prepareStatement(INVOCATIONS_ON_A_BRANCH)) {
            PipelineState.bindTheBranch(query, projectClone, branch);
            return spendOf(query);
        } catch (SQLException unreadable) {
            return Optional.empty();
        }
    }

    private static Optional<TokenUsage> spendOf(PreparedStatement query) throws SQLException {
        try (ResultSet invocations = query.executeQuery()) {
            TokenUsage spend = TokenUsage.NONE;
            while (invocations.next()) {
                if (invocations.getObject("input") == null) {
                    return Optional.empty();
                }
                spend = spend.plus(spentBy(invocations));
            }
            return Optional.of(spend);
        }
    }

    private static TokenUsage spentBy(ResultSet invocation) throws SQLException {
        long input = invocation.getLong("input");
        long cacheRead = invocation.getLong("cache_read");
        long cacheWrite = invocation.getLong("cache_write");
        return new TokenUsage(uncachedInput(input, cacheRead, cacheWrite), cacheRead, cacheWrite, invocation.getLong("output"));
    }

    private static long uncachedInput(long input, long cacheRead, long cacheWrite) {
        return inputHoldsItsCache(input, cacheRead, cacheWrite) ? freshInput(input, cacheRead) - cacheWrite : input;
    }

    private static boolean inputHoldsItsCache(long input, long cacheRead, long cacheWrite) {
        return input >= cacheRead + cacheWrite;
    }

    private static long freshInput(long input, long cacheRead) {
        return input - cacheRead;
    }
}
