package io.github.ythibos.edi.adapter.pipeline;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.delivery.RemoteAddress;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

final class CommitKeys {

    private static final String REPOSITORY_OVERRIDES = "repository_overrides";
    private static final String COMMIT = "commit";
    private static final String BRANCH_PATTERN = "branch_pattern";
    private static final String BRANCH_REPLACEMENT = "branch_replacement";
    private static final String FIX_MESSAGE = "fix_message";
    private static final String KEYED_FIX_MESSAGE = "{{.Branch}} {{.Summary}}";
    private static final String PULL_REQUEST = "pr";
    private static final String TITLE_FORMAT = "title_format";
    private static final String KEYED_TITLE = "{{.Branch}} {{.Title}}";

    private CommitKeys() {
    }

    static void keyCommitsPushedTo(Path globalConfiguration, String remoteUrl) {
        PipelineConfiguration.changeEntry(globalConfiguration, REPOSITORY_OVERRIDES, overrides -> {
            ObjectNode override = takeTheEntriesFor(overrides, remoteUrl);
            keyTheCommitsIn(override);
            overrides.set(remoteUrl, override);
        });
    }

    private static ObjectNode takeTheEntriesFor(ObjectNode overrides, String remoteUrl) {
        ObjectNode override = overrides.objectNode();
        remotesOfTheRepository(overrides, remoteUrl).forEach(remote -> combineInto(override, overrides.remove(remote)));
        return override;
    }

    private static List<String> remotesOfTheRepository(ObjectNode overrides, String remoteUrl) {
        return overrides.propertyNames().stream()
                .filter(remote -> remote.equals(remoteUrl) || namesTheSameRepository(remote, remoteUrl))
                .sorted(exactRemoteLast(remoteUrl))
                .toList();
    }

    private static boolean namesTheSameRepository(String remote, String remoteUrl) {
        return RemoteAddress.of(remote)
                .flatMap(address -> RemoteAddress.of(remoteUrl).filter(address::isTheSameRepositoryAs))
                .isPresent();
    }

    private static Comparator<String> exactRemoteLast(String remoteUrl) {
        return Comparator.comparing(remoteUrl::equals);
    }

    private static void combineInto(ObjectNode combined, JsonNode entry) {
        entry.properties().forEach(field -> {
            if (combined.get(field.getKey()) instanceof ObjectNode existing && field.getValue().isObject()) {
                combineInto(existing, field.getValue());
            } else {
                combined.set(field.getKey(), field.getValue().deepCopy());
            }
        });
    }

    private static void keyTheCommitsIn(ObjectNode override) {
        ObjectNode commit = fieldsOf(override, COMMIT)
                .put(BRANCH_PATTERN, BranchName.workKeyCapture().pattern())
                .put(FIX_MESSAGE, KEYED_FIX_MESSAGE);
        commit.remove(BRANCH_REPLACEMENT);
        fieldsOf(override, PULL_REQUEST).put(TITLE_FORMAT, KEYED_TITLE);
    }

    private static ObjectNode fieldsOf(ObjectNode override, String name) {
        return override.get(name) instanceof ObjectNode fields ? fields : override.putObject(name);
    }
}
