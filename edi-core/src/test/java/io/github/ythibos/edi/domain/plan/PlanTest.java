package io.github.ythibos.edi.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.AwaitedStage;
import io.github.ythibos.edi.domain.task.CaptainHold;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskStage;

class PlanTest {

    private static final Instant AT = Instant.parse("2026-11-13T09:00:00Z");
    private static final PlanHeading HEADING = new PlanHeading(WorkKey.parse("HRN-40"), "Light the harbour", new ProjectName("harness"),
            "docs/research/HRN-40-plan.md", "D44", 2, EffortLevel.XHIGH);
    private static final List<TaskId> TASKS = List.of(new TaskId("fit-lamp"), new TaskId("paint-lamp"), new TaskId("oil-gate"));

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
    void showsSlicesOneAndThreeEligibleThenTwoOnceOneIsMerged() {
        Plan plan = threeSlices();

        assertThat(eligible(plan, queued())).containsExactly(1, 3);
        assertThat(eligible(plan, with(queued(), "fit-lamp", TaskStage.WORKING))).containsExactly(3);
        assertThat(eligible(plan, with(queued(), "fit-lamp", TaskStage.MERGED))).containsExactly(2, 3);
    }

    @Test
    void namesTheWaitOfEachSliceWithTheStageOfTheTaskItWaitsFor() {
        List<SliceStanding> standings = threeSlices().standings(with(queued(), "fit-lamp", TaskStage.READY));

        assertThat(standings.get(1).awaits()).hasValueSatisfying(wait -> {
            assertThat(wait.task()).isEqualTo(new TaskId("fit-lamp"));
            assertThat(wait.awaited()).isEqualTo(AwaitedStage.MERGED);
            assertThat(wait.stage()).isEqualTo(TaskStage.READY);
        });
        assertThat(standings.get(2).awaits()).isEmpty();
    }

    @Test
    void aHeldSliceIsNotEligibleAndADroppedSliceHoldsNobodyBack() {
        Map<TaskId, Task> tasks = with(queued(), "fit-lamp", TaskStage.DROPPED);
        Task oilGate = tasks.get(new TaskId("oil-gate"));
        tasks.put(oilGate.id(), oilGate.held(new CaptainHold(HoldKind.APPROVAL, "needs a yes", HoldUntil.captainsWord(), AT)));

        assertThat(eligible(threeSlices(), tasks)).containsExactly(2);
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

    private static Plan threeSlices() {
        return new PlanDraft(HEADING, List.of(slice(1, "HRN-41", null), slice(2, "HRN-42", null), slice(3, "HRN-43", "none"))).planned(TASKS);
    }

    private static Map<TaskId, Task> queued() {
        return TASKS.stream()
                .map(id -> Task.queued(id, new Intake(TaskKind.SHIP, id.value(), List.of(HEADING.project()), null, null, List.of(), null), "", AT))
                .collect(Collectors.toMap(Task::id, Function.identity()));
    }

    private static Map<TaskId, Task> with(Map<TaskId, Task> tasks, String id, TaskStage stage) {
        Task task = tasks.get(new TaskId(id));
        tasks.put(task.id(), new Task(task.id(), task.intake(), stage, task.notes(), null, task.created(), stage.isClosed() ? AT : null));
        return tasks;
    }

    private static List<Integer> eligible(Plan plan, Map<TaskId, Task> tasks) {
        return plan.standings(tasks).stream().filter(SliceStanding::isEligible).map(standing -> standing.slice().number()).toList();
    }
}
