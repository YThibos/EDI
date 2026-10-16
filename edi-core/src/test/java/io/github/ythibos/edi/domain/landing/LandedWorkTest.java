package io.github.ythibos.edi.domain.landing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class LandedWorkTest {

    private static final BranchCommit UNLANDED = new BranchCommit("1111111111111111", "HRN-3 Fit the lamp", false);
    private static final BranchCommit REBUILT = new BranchCommit("2222222222222222", "HRN-3 Fit the horn", true);

    @Test
    void isLandedWhenNothingIsUncommittedAndEveryCommitIsInTheBase() {
        assertThat(work(List.of(), List.of(), List.of(REBUILT), false, CaptainsCopy.NOT_DELIVERED_THERE).isLanded()).isTrue();
        assertThat(work(List.of(), List.of(), List.of(UNLANDED, REBUILT), true, CaptainsCopy.HOLDS_THE_HEAD).isLanded()).isTrue();
    }

    @Test
    void namesEveryPieceOfUnlandedWork() {
        LandedWork unlanded = work(List.of("lamp.txt"), List.of("vendor/tool/"), List.of(UNLANDED, REBUILT), false, CaptainsCopy.LACKS_THE_HEAD);

        assertThat(unlanded.isLanded()).isFalse();
        assertThat(unlanded.unlandedCommits()).containsExactly(UNLANDED);
        assertThat(unlanded.findings()).containsExactly(
                "lamp.txt is not committed",
                "vendor/tool/ is a repository nested in the copy, and nothing of it is landed",
                "commit 111111111111 \"HRN-3 Fit the lamp\" is not in origin/main",
                "the captain's checkout does not hold the branch's head");
    }

    private static LandedWork work(List<String> uncommitted, List<String> nested, List<BranchCommit> commits, boolean content, CaptainsCopy copy) {
        return new LandedWork("origin/main", uncommitted, nested, commits, content, copy);
    }
}
