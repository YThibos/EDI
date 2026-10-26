package io.github.ythibos.edi.application.port;

import java.nio.file.Path;
import java.util.Optional;

import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.delivery.PipelineReview;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.worker.TokenUsage;

public interface Pipeline {

    Optional<PipelineRun> latestRunOf(Path worktree, BranchName branch);

    Optional<TokenUsage> tokensSpentOn(Path projectClone, BranchName branch);

    Optional<PipelineReview> reviewOf(Path projectClone, BranchName branch);

    void runAgentOn(Route route);

    void keyCommitsPushedTo(String remoteUrl);
}
