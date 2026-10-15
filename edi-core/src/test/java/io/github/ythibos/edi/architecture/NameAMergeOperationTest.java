package io.github.ythibos.edi.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NameAMergeOperationTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "merge", "mergePullRequest", "MergeDraft", "enableAutoMerge", "automerge", "auto_merge",
        "mergeWhenPipelineSucceeds", "merge_when_pipeline_succeeds", "setMergeWhenPipelineSucceeds"})
    void recognisesANameThatPerformsAMerge(String name) {
        assertThat(NameAMergeOperation.namesAMergeOperation(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"isMerged", "mergedAt", "mergeRequest", "mergeRequestState", "observeMergeState", "DraftMerged"})
    void acceptsANameThatOnlyObservesAMerge(String name) {
        assertThat(NameAMergeOperation.namesAMergeOperation(name)).isFalse();
    }
}
