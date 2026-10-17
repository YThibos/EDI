package io.github.ythibos.edi.adapter.pipeline;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.Program;
import io.github.ythibos.edi.adapter.process.ProgramCall;
import io.github.ythibos.edi.application.port.Pipeline;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.delivery.PipelineReview;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import tools.jackson.databind.json.JsonMapper;

@Component
public class NoMistakesPipeline implements Pipeline {

    private static final String STATE_DATABASE = "state.sqlite";

    private final ExternalPrograms programs;
    private final JsonMapper json;
    private final String executable;
    private final Path stateDatabase;

    public NoMistakesPipeline(ExternalPrograms programs, JsonMapper json, @Value("${edi.pipeline.no-mistakes:}") String executable,
            @Value("${edi.pipeline.home:${user.home}/.no-mistakes}") Path home) {
        this.programs = programs;
        this.json = json;
        this.executable = executable;
        this.stateDatabase = home.resolve(STATE_DATABASE);
    }

    @Override
    public Optional<PipelineRun> latestRunOf(Path worktree, BranchName branch) {
        String status = programs.run(ProgramCall.of(Program.NO_MISTAKES, executable, worktree, List.of("axi", "status"))).outputOfSuccess();
        return AxiStatus.currentRunIn(status, branch.toString());
    }

    @Override
    public Optional<TokenUsage> tokensSpentOn(Path projectClone, BranchName branch) {
        return PipelineSpend.recordedIn(stateDatabase, projectClone, branch);
    }

    @Override
    public Optional<PipelineReview> reviewOf(Path projectClone, BranchName branch) {
        return PipelineReviews.recordedIn(stateDatabase, projectClone, branch, json);
    }
}
