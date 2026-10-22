package io.github.ythibos.edi.domain.delivery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.task.BaseBranch;

public record MergeRequestView(
        MergeRequestAddress address,
        MergeRequestState state,
        boolean draft,
        String sourceBranch,
        String targetBranch,
        String headCommit,
        String landedCommit,
        Instant mergedAt) {

    private static final int SHORT_COMMIT = 12;

    public MergeRequestView {
        Objects.requireNonNull(address, "A merge request is seen at its address.");
        Objects.requireNonNull(state, () -> address + " is seen open, merged or closed.");
        if (state == MergeRequestState.MERGED && (landedCommit == null || landedCommit.isBlank())) {
            throw new IllegalArgumentException(address + " is merged, so the forge names the commit it landed on its base.");
        }
        if (state == MergeRequestState.MERGED && mergedAt == null) {
            throw new IllegalArgumentException(address + " is merged, so the forge names when it was merged.");
        }
    }

    public static Optional<MergeRequestView> deliveryAmong(List<MergeRequestView> fromOneBranch) {
        return fromOneBranch.stream().filter(request -> request.state() == MergeRequestState.OPEN).findFirst()
                .or(() -> fromOneBranch.stream().filter(MergeRequestView::isMerged).findFirst());
    }

    public boolean isMerged() {
        return state == MergeRequestState.MERGED;
    }

    public boolean mergedSince(Instant moment) {
        return isMerged() && !mergedAt.isBefore(moment);
    }

    public List<String> gapsAsTheDeliveryOf(BranchName branch, BaseBranch base, String head) {
        List<String> gaps = new ArrayList<>();
        if (state == MergeRequestState.CLOSED) {
            gaps.add(address + " is closed without a merge");
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
