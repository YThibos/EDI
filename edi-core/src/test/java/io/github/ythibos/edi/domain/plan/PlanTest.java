package io.github.ythibos.edi.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.AwaitedStage;

class PlanTest {

    private static final PlanHeading HEADING = new PlanHeading(WorkKey.parse("HRN-40"), "Light the harbour", new ProjectName("harness"),
            "docs/research/HRN-40-plan.md", "D44", 2, EffortLevel.XHIGH);

    @Test
    void readsAWaitFromItsWords() {
        assertThat(SliceWait.parse("after 3 merged")).isEqualTo(new SliceWait.After(3, AwaitedStage.MERGED));
        assertThat(SliceWait.parse("after 12 landed")).isEqualTo(new SliceWait.After(12, AwaitedStage.LANDED));
        assertThat(SliceWait.parse("none")).isEqualTo(new SliceWait.None());
        assertThat(SliceWait.parse("after 3 merged").words()).isEqualTo("after 3 merged");
        assertThatIllegalArgumentException().isThrownBy(() -> SliceWait.parse("after slice 3 merged"))
                .withMessage("A slice's wait is \"after N merged\", \"after N landed\" or \"none\", not \"after slice 3 merged\".");
    }

    @Test
    void aSliceWaitsForTheSliceBeforeToBeMergedUnlessItNamesItsWait() {
        PlanDraft draft = new PlanDraft(HEADING, List.of(slice(3, "HRN-43", null), slice(1, "HRN-41", null), slice(2, "HRN-42", "after 1 landed")));

        assertThat(draft.slices()).extracting(SliceDraft::number, slice -> slice.awaits().words())
                .containsExactly(tuple(1, "none"), tuple(2, "after 1 landed"), tuple(3, "after 2 merged"));
    }

    @Test
    void refusesSlicesNumberedTwiceSharingAKeyOrWaitingForALaterSlice() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PlanDraft(HEADING, List.of(slice(1, "HRN-41", null), slice(1, "HRN-42", null))))
                .withMessage("The plan numbers slice 1 twice.");
        assertThatIllegalArgumentException().isThrownBy(() -> new PlanDraft(HEADING, List.of(slice(1, "HRN-41", null), slice(2, "HRN-41", null))))
                .withMessage("Slice 2 shares the key HRN-41 with an earlier slice; each slice has its own key, because tasks that share a key"
                        + " share a branch.");
        assertThatIllegalArgumentException().isThrownBy(() -> new PlanDraft(HEADING, List.of(slice(1, "HRN-41", "after 2 merged"),
                        slice(2, "HRN-42", "none"))))
                .withMessage("Slice 1 waits after 2 merged, and a slice waits only for an earlier slice of its plan.");
        assertThatIllegalArgumentException().isThrownBy(() -> new PlanDraft(HEADING, List.of()))
                .withMessage("The plan HRN-40 has at least one slice.");
    }

    @Test
    void refusesAPlanWithoutItsGoOrRoomForASlice() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PlanHeading(HEADING.key(), "Light the harbour", HEADING.project(), "report.md", " ", 2,
                null)).withMessage("The plan HRN-40 names the decision that gave the go.");
        assertThatIllegalArgumentException().isThrownBy(() -> new PlanHeading(HEADING.key(), "Light the harbour", HEADING.project(), "report.md", "D44", 0,
                null)).withMessage("The plan HRN-40 works on at least one slice at a time: 0");
    }

    private static SliceDraft slice(int number, String key, String wait) {
        return new SliceDraft(number, "Slice " + number, CategoryId.IMPLEMENTATION, WorkKey.parse(key), "", wait == null ? null : SliceWait.parse(wait));
    }
}
