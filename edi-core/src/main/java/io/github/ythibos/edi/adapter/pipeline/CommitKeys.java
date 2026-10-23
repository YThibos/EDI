package io.github.ythibos.edi.adapter.pipeline;

import java.nio.file.Path;

import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.delivery.RemoteAddress;
import tools.jackson.databind.node.ObjectNode;

final class CommitKeys {

    private static final String REPOSITORY_OVERRIDES = "repository_overrides";
    private static final String COMMIT = "commit";
    private static final String BRANCH_PATTERN = "branch_pattern";
    private static final String FIX_MESSAGE = "fix_message";
    private static final String KEYED_FIX_MESSAGE = "{{.Branch}} {{.Summary}}";
    private static final String PULL_REQUEST = "pr";
    private static final String TITLE_FORMAT = "title_format";
    private static final String KEYED_TITLE = "{{.Branch}} {{.Title}}";

    private CommitKeys() {
    }

    static void keyCommitsPushedTo(Path globalConfiguration, String remoteUrl) {
        PipelineConfiguration.changeEntry(globalConfiguration, REPOSITORY_OVERRIDES, overrides -> {
            dropTheEntriesFor(overrides, remoteUrl);
            keyTheCommitsIn(overrides.putObject(remoteUrl));
        });
    }

    private static void dropTheEntriesFor(ObjectNode overrides, String remoteUrl) {
        RemoteAddress.of(remoteUrl).ifPresent(repository -> overrides.remove(overrides.propertyNames().stream()
                .filter(remote -> RemoteAddress.of(remote).filter(repository::isTheSameRepositoryAs).isPresent())
                .toList()));
    }

    private static void keyTheCommitsIn(ObjectNode override) {
        override.putObject(COMMIT)
                .put(BRANCH_PATTERN, BranchName.workKeyCapture().pattern())
                .put(FIX_MESSAGE, KEYED_FIX_MESSAGE);
        override.putObject(PULL_REQUEST).put(TITLE_FORMAT, KEYED_TITLE);
    }
}
