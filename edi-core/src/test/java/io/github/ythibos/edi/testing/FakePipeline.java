package io.github.ythibos.edi.testing;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.ythibos.edi.application.port.Pipeline;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.delivery.PipelineReview;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.worker.TokenUsage;

public final class FakePipeline implements Pipeline {

    private final Map<String, PipelineRun> runs = new HashMap<>();
    private final List<String> keyedRemotes = new ArrayList<>();
    private TokenUsage spend;
    private PipelineReview review;
    private Route agentRoute;

    @Override
    public synchronized Optional<PipelineRun> latestRunOf(Path worktree, BranchName branch) {
        return Optional.ofNullable(runs.get(branch.toString()));
    }

    @Override
    public synchronized Optional<TokenUsage> tokensSpentOn(Path projectClone, BranchName branch) {
        return Optional.ofNullable(spend);
    }

    @Override
    public synchronized Optional<PipelineReview> reviewOf(Path projectClone, BranchName branch) {
        return Optional.ofNullable(review);
    }

    @Override
    public synchronized void runAgentOn(Route route) {
        agentRoute = route;
    }

    @Override
    public synchronized void keyCommitsPushedTo(String remoteUrl) {
        keyedRemotes.add(remoteUrl);
    }

    public synchronized void runs(BranchName branch, PipelineRun run) {
        runs.put(branch.toString(), run);
    }

    public synchronized void spent(TokenUsage tokens) {
        spend = tokens;
    }

    public synchronized void reviewed(PipelineReview counted) {
        review = counted;
    }

    public synchronized Route agentRoute() {
        return agentRoute;
    }

    public synchronized List<String> keyedRemotes() {
        return List.copyOf(keyedRemotes);
    }
}
