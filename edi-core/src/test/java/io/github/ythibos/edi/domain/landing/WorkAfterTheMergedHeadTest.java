package io.github.ythibos.edi.domain.landing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.authority.CaptainWordAct;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.HeadChecks;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.task.TaskId;

class WorkAfterTheMergeTest {

    private static final TaskId LAMP = new TaskId("fit-lamp");
    private static final MergeRequestView MERGED = new MergeRequestView(
            new MergeRequestAddress(ForgeRepository.registered(ForgeKind.GITHUB, "github.com", "example-owner/harness"), 7), MergeRequestState.MERGED,
            false, "feature/HRN-3", "main", "7e23dd8036558de2", HeadChecks.PASSED, "c0ffee0000000000", Instant.parse("2026-11-24T09:00:00Z"));
    private static final BranchCommit CAP = new BranchCommit("b30e15a1c2d3e4f5", "HRN-3 Fit the cap", false);
    private static final BranchCommit SPEC = new BranchCommit("d4e5f6a7b8c9d0e1", "HRN-3 Amend the spec", false);

    @Test
    void asksToFollowUpOrDiscardEveryCommitAfterTheMergedHead() {
        DecisionCard card = new WorkAfterTheMergedHead(LAMP, MERGED, List.of(CAP, SPEC)).card();

        assertThat(card.context()).isEqualTo(String.join("\n",
                "The forge merged https://github.com/example-owner/harness/pull/7 at 7e23dd8036558de2, while fit-lamp went on:"
                        + " 2 commits after the merged head are not in main:",
                "- b30e15a1c2d3 \"HRN-3 Fit the cap\"",
                "- d4e5f6a7b8c9 \"HRN-3 Amend the spec\"",
                "fit-lamp stays merged, and its clean-up is refused, until they land or are discarded."));
        assertThat(card.recommendation().key()).isEqualTo(new OptionKey('A'));
        assertThat(card.authorises(new OptionKey('B'), CaptainWordAct.DISCARD_WORK)).isTrue();
        assertThat(card.authorises(new OptionKey('A'), CaptainWordAct.DISCARD_WORK)).isFalse();
    }

    @Test
    void namesAtLeastOneCommitAfterTheMergedHead() {
        assertThatIllegalArgumentException().isThrownBy(() -> new WorkAfterTheMergedHead(LAMP, MERGED, List.of()))
                .withMessage("Work after the merge of fit-lamp names at least one commit after the merged head.");
    }
}
