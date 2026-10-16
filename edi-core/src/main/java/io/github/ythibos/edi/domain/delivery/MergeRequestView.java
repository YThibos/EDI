package io.github.ythibos.edi.domain.delivery;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.task.BaseBranch;

public record MergeRequestView(
        MergeRequestAddress address,
        MergeRequestState state,
        boolean draft,
        String sourceBranch,
        String targetBranch,
        String headCommit,
        String landedCommit) {

    private static final int SHORT_COMMIT = 12;

    public MergeRequestView {
        Objects.requireNonNull(address, "A merge request is seen at its address.");
        Objects.requireNonNull(state, () -> address + " is seen open, merged or closed.");
        if (state == MergeRequestState.MERGED && (landedCommit == null || landedCommit.isBlank())) {
            throw new IllegalArgumentException(address + " is merged, so the forge names the commit it landed on its base.");
        }
    }

    public boolean isMerged() {
        return state == MergeRequestState.MERGED;
    }

    public List<String> gapsAsTheDeliveryOf(BranchName branch, BaseBranch base, String head) {
        List<String> gaps = new ArrayList<>();
        if (state != MergeRequestState.OPEN) {
            gaps.add(address + " is " + state.word() + ", not open");
        }
        if (!draft) {
            gaps.add(address + " is not a draft; every merge request EDI delivers stays a draft until the captain merges it");
        }
        if (!branch.toString().equals(sourceBranch)) {
            gaps.add(address + " comes from " + sourceBranch + ", not from " + branch);
        }
        if (!base.name().equals(targetBranch)) {
            gaps.add(address + " targets " + targetBranch + ", not " + base);
        }
        if (!head.equals(headCommit)) {
            gaps.add(address + " shows head " + abbreviated(headCommit) + ", but the isolated copy is at " + abbreviated(head)
                    + "; push the copy's head first");
        }
        return List.copyOf(gaps);
    }

    private static String abbreviated(String commit) {
        return commit == null ? "none" : commit.substring(0, Math.min(commit.length(), SHORT_COMMIT));
    }
}
