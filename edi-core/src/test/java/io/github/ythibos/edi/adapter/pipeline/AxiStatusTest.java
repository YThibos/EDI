package io.github.ythibos.edi.adapter.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.delivery.PipelineRun;

class AxiStatusTest {

    @Test
    void readsTheRunningRunOfTheCurrentBranchWithItsActiveStep() throws IOException {
        assertThat(AxiStatus.currentRunIn(fixture("status-with-a-running-run.toon"), "feature/HRN-3")).contains(new PipelineRun(
                "01M527P0FFESZMZCVXFT9VHVW5", "running", "ci", "aacda7bfd12398c1d7960f58d68177e003550ad7",
                "https://github.com/example-owner/harness/pull/6"));
    }

    @Test
    void readsAFinishedRunWithoutAnActiveStepOrAMergeRequest() throws IOException {
        PipelineRun failed = AxiStatus.currentRunIn(fixture("status-with-a-failed-run.toon"), "feature/HRN-3").orElseThrow();

        assertThat(failed.status()).isEqualTo("failed");
        assertThat(failed.isRunning()).isFalse();
        assertThat(failed.activeStep()).isNull();
        assertThat(failed.mergeRequestUrl()).isNull();
    }

    @Test
    void findsNoRunWhenTheBranchHasNoneOrTheBlockIsAnotherBranchs() throws IOException {
        assertThat(AxiStatus.currentRunIn(fixture("status-without-a-run.toon"), "feature/HRN-3")).isEmpty();
        assertThat(AxiStatus.currentRunIn(fixture("status-with-a-running-run.toon"), "feature/HRN-4")).isEmpty();
        assertThat(AxiStatus.currentRunIn("", "feature/HRN-3")).isEmpty();
    }

    private static String fixture(String name) throws IOException {
        try (InputStream content = AxiStatusTest.class.getResourceAsStream("/no-mistakes/" + name)) {
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
