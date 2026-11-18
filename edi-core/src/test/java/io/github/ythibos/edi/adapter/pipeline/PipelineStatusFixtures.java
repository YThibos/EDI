package io.github.ythibos.edi.adapter.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.delivery.PipelineRun;

public final class PipelineStatusFixtures {

    private PipelineStatusFixtures() {
    }

    public static PipelineRun runIn(String fixture, BranchName branch) throws IOException {
        return AxiStatus.currentRunIn(statusIn(fixture), branch.toString()).orElseThrow();
    }

    static String statusIn(String fixture) throws IOException {
        try (InputStream content = PipelineStatusFixtures.class.getResourceAsStream("/no-mistakes/" + fixture)) {
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
