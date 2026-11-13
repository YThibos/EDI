package io.github.ythibos.edi.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.AwaitedStage;
import io.github.ythibos.edi.domain.task.CaptainHold;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskStage;
import io.github.ythibos.edi.domain.task.Wait;

class SliceStandingTest {

    private static final Instant AT = Instant.parse("2026-11-13T09:00:00Z");
    private static final TaskId FIT_LAMP = new TaskId("fit-lamp");
    private static final TaskId PAINT_LAMP = new TaskId("paint-lamp");
    private static final TaskId PLAN_THE_PAINT = new TaskId("plan-work-on-paint-lamp");
    private static final Slice PAINT = new Slice(2, PAINT_LAMP, CategoryId.IMPLEMENTATION, new SliceWait.After(1, AwaitedStage.MERGED));

    @Test
    void aQueuedSliceIsEligibleOnceTheSliceItWaitsForIsMerged() {
        assertThat(standing(paintLamp(), afterFitLampMerged(TaskStage.WORKING)).isEligible()).isFalse();
        assertThat(standing(paintLamp(), afterFitLampMerged(TaskStage.MERGED)).isEligible()).isTrue();
        assertThat(standing(paintLamp()).isEligible()).isTrue();
    }

    @Test
    void aSliceIsNotEligibleWhileAnyOtherWaitOfItsTaskIsUnmet() {
        Wait onItsScout = Wait.afterLanded(PLAN_THE_PAINT, TaskStage.QUEUED);

        assertThat(standing(paintLamp(), afterFitLampMerged(TaskStage.MERGED), onItsScout).isEligible()).isFalse();
    }

    @Test
    void aSliceAlreadyAtWorkOrHeldIsNotEligibleAndADroppedSliceHoldsNobodyBack() {
        Task working = new Task(PAINT_LAMP, paintLamp().intake(), TaskStage.WORKING, "", null, AT, null);
        Task held = paintLamp().held(new CaptainHold(HoldKind.APPROVAL, "needs a yes", HoldUntil.captainsWord(), AT));

        assertThat(standing(working).isEligible()).isFalse();
        assertThat(standing(held).isEligible()).isFalse();
        assertThat(standing(paintLamp(), afterFitLampMerged(TaskStage.DROPPED)).isEligible()).isTrue();
    }

    private static Task paintLamp() {
        return Task.queued(PAINT_LAMP, new Intake(TaskKind.SHIP, "Paint the lamp", List.of(new ProjectName("harness")), null, null, List.of(), null), "",
                AT);
    }

    private static Wait afterFitLampMerged(TaskStage stage) {
        return new Wait(FIT_LAMP, AwaitedStage.MERGED, stage);
    }

    private static SliceStanding standing(Task task, Wait... waits) {
        return new SliceStanding(PAINT, task, List.of(waits));
    }
}
