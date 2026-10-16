package io.github.ythibos.edi.domain.delivery;

import java.util.Objects;

import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.task.BaseBranch;

public record DraftProposal(BranchName branch, BaseBranch base, String title, String description) {

    public DraftProposal {
        Objects.requireNonNull(branch, "A draft is opened from a branch.");
        Objects.requireNonNull(base, () -> "The draft of " + branch + " targets a base branch.");
        title = Objects.requireNonNullElse(title, "").strip();
        description = Objects.requireNonNullElse(description, "").strip();
        String key = branch.workKey().toString();
        if (!title.startsWith(key + " ") || title.lines().count() != 1) {
            throw new IllegalArgumentException("A draft's title is one line that starts with its work key " + key + ": " + title);
        }
        AgentAttribution.firstIn(description).ifPresent(line -> {
            throw new IllegalArgumentException("A draft's description names no agent or tool as its author, so it never carries \"" + line + "\".");
        });
    }

    public static DraftProposal forWork(BranchName branch, BaseBranch base, String taskTitle, String description) {
        return new DraftProposal(branch, base, branch.workKey() + " " + taskTitle.strip(), description);
    }
}
