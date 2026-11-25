package io.github.ythibos.edi.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.AwaitedStage;
import io.github.ythibos.edi.domain.task.Hold;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.Holds;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskStage;
import io.github.ythibos.edi.domain.task.Wait;

class PlanStandingsTest {

    private static final Instant AT = Instant.parse("2026-11-24T18:00:00Z");

    @Test
    void leavesAPlaceForEachSliceTheCapacityAllowsBeyondThoseAtWork() {
        assertThat(planOf(2, queued(1), queued(2), queued(3)).freePlaces()).isEqualTo(2);
        assertThat(planOf(2, at(1, TaskStage.WORKING), queued(2), queued(3)).freePlaces()).isEqualTo(1);
        assertThat(planOf(2, at(1, TaskStage.WORKING), at(2, TaskStage.CHANGING), queued(3)).freePlaces()).isZero();
    }

    @Test
    void freesTheirPlacesOnceSlicesAreDelivered() {
        assertThat(planOf(2, at(1, TaskStage.DELIVERED), at(2, TaskStage.MERGED), at(3, TaskStage.WORKING)).freePlaces()).isEqualTo(1);
    }

    @Test
    void keepsTheirPlacesForSlicesDispatchedAndStillWaitingOnTheirResearch() {
        SliceStanding waitingOnItsResearch = new SliceStanding(sliceNumbered(1), task(1, TaskStage.QUEUED),
                List.of(Wait.afterLanded(new TaskId("plan-work-on-slice-1"), TaskStage.WORKING)), true);

        assertThat(planOf(2, waitingOnItsResearch, at(2, TaskStage.WORKING), queued(3)).freePlaces()).isZero();
        assertThat(planOf(2, queued(1), at(2, TaskStage.WORKING), queued(3)).freePlaces()).isEqualTo(1);
    }

    @Test
    void givesASliceDispatchedBeforeAndEligibleAgainAPlaceToBeDispatchedAgain() {
        SliceStanding dispatchedAndEligible = new SliceStanding(sliceNumbered(1), task(1, TaskStage.QUEUED),
                List.of(Wait.afterLanded(new TaskId("plan-work-on-slice-1"), TaskStage.LANDED)), true);
        PlanStandings standings = planOf(1, dispatchedAndEligible, queued(2));

        assertThat(standings.freePlaces()).isEqualTo(1);
        assertThat(numbers(standings.eligible())).containsExactly(1, 2);
    }

    @Test
    void namesTheEligibleSlicesInTheirOrderAndNeverAHeldSliceOrOneWhoseWaitIsNotMet() {
        SliceStanding held = new SliceStanding(sliceNumbered(1), task(1, TaskStage.QUEUED)
                .held(new Hold.Approval("needs a yes", HoldUntil.captainsWord(), AT)), List.of(), false);
        SliceStanding waiting = new SliceStanding(sliceNumbered(2), task(2, TaskStage.QUEUED),
                List.of(new Wait(new TaskId("slice-1"), AwaitedStage.MERGED, TaskStage.QUEUED)), false);

        assertThat(numbers(planOf(3, held, waiting, queued(3), at(4, TaskStage.WORKING), queued(5)).eligible())).containsExactly(3, 5);
    }

    private static PlanStandings planOf(int capacity, SliceStanding... slices) {
        PlanHeading heading = new PlanHeading(WorkKey.parse("HRN-40"), "Light the harbour", new ProjectName("harness"), "docs/research/HRN-40-plan.md",
                PlanGo.named("D44"), capacity, null);
        List<SliceStanding> standings = List.of(slices);
        return new PlanStandings(new Plan(heading, standings.stream().map(SliceStanding::slice).toList()), standings);
    }

    private static SliceStanding queued(int number) {
        return at(number, TaskStage.QUEUED);
    }

    private static SliceStanding at(int number, TaskStage stage) {
        return new SliceStanding(sliceNumbered(number), task(number, stage), List.of(), false);
    }

    private static Slice sliceNumbered(int number) {
        return new Slice(number, new TaskId("slice-" + number), CategoryId.IMPLEMENTATION, new SliceWait.None());
    }

    private static Task task(int number, TaskStage stage) {
        Intake intake = new Intake(TaskKind.SHIP, "Slice " + number, List.of(new ProjectName("harness")), null, null, List.of(), null, null);
        return new Task(new TaskId("slice-" + number), intake, stage, "", Holds.none(), AT, null);
    }

    private static List<Integer> numbers(List<SliceStanding> standings) {
        return standings.stream().map(standing -> standing.slice().number()).toList();
    }
}
