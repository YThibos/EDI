package io.github.ythibos.edi.application.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.SliceDispatched;
import io.github.ythibos.edi.domain.plan.PlanDraft;
import io.github.ythibos.edi.domain.plan.PlanHeading;
import io.github.ythibos.edi.domain.plan.SliceDraft;
import io.github.ythibos.edi.domain.plan.SliceWait;
import io.github.ythibos.edi.domain.presence.Mandate;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.AwaitedStage;
import io.github.ythibos.edi.domain.task.CaptainHold;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskStage;
import io.github.ythibos.edi.domain.wake.Wake;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class DispatchEligibleSlicesTest {

    private static final TaskId FIT_LAMP = new TaskId("fit-the-lamp");
    private static final TaskId PAINT_LAMP = new TaskId("paint-the-lamp");
    private static final TaskId OIL_GATE = new TaskId("oil-the-gate");
    private static final SliceWait NONE = new SliceWait.None();

    @TempDir
    private Path directory;

    @Test
    void dispatchesTwoOfThreeEligibleSlicesWithinACapacityOfTwoAndTheThirdOnceOneIsDelivered() throws IOException {
        TestHome home = WorkerFixtures.homeWithProjects(directory);
        home.addPlan().add(threeSlices(2, NONE, NONE, CategoryId.IMPLEMENTATION), Actor.PRIMARY);

        List<SliceDispatched> firstRound = home.dispatchEligibleSlices().dispatchWhereThePlansHaveRoom();
        List<SliceDispatched> whileBothWork = home.dispatchEligibleSlices().dispatchWhereThePlansHaveRoom();
        delivers(home, FIT_LAMP);
        List<SliceDispatched> onceOneIsDelivered = home.dispatchEligibleSlices().dispatchWhereThePlansHaveRoom();

        assertThat(firstRound).extracting(SliceDispatched::task).containsExactly(FIT_LAMP, PAINT_LAMP);
        assertThat(whileBothWork).isEmpty();
        assertThat(home.tasks().get(FIT_LAMP).stage()).isEqualTo(TaskStage.DELIVERED);
        assertThat(onceOneIsDelivered).extracting(SliceDispatched::task).containsExactly(OIL_GATE);
        assertThat(home.tasks().get(OIL_GATE).stage()).isEqualTo(TaskStage.WORKING);
        assertThat(wakesOf(home, WakeKind.SLICE_DISPATCHED)).extracting(Wake::task, Wake::detail).containsExactly(
                tuple(FIT_LAMP, "1 of the HRN-40 plan"),
                tuple(PAINT_LAMP, "2 of the HRN-40 plan"),
                tuple(OIL_GATE, "3 of the HRN-40 plan"));
    }

    @Test
    void dispatchesNeitherAHeldSliceNorOneWhoseWaitIsNotMet() throws IOException {
        TestHome home = WorkerFixtures.homeWithProjects(directory);
        home.addPlan().add(threeSlices(2, new SliceWait.After(1, AwaitedStage.MERGED), NONE, CategoryId.IMPLEMENTATION), Actor.PRIMARY);
        home.tasks().update(home.tasks().get(OIL_GATE).held(new CaptainHold(HoldKind.APPROVAL, "the gate needs a yes", HoldUntil.captainsWord(),
                TestHome.START)));

        List<SliceDispatched> dispatched = home.dispatchEligibleSlices().dispatchWhereThePlansHaveRoom();

        assertThat(dispatched).extracting(SliceDispatched::task).containsExactly(FIT_LAMP);
        assertThat(home.tasks().get(PAINT_LAMP).stage()).isEqualTo(TaskStage.QUEUED);
        assertThat(home.tasks().get(OIL_GATE).stage()).isEqualTo(TaskStage.QUEUED);
        assertThat(home.dispatches().of(PAINT_LAMP)).isEmpty();
        assertThat(home.dispatches().of(OIL_GATE)).isEmpty();
    }

    @Test
    void dispatchesNothingWhileItsProjectRunsAsManyWorkersAsItsCapacity() throws IOException {
        TestHome home = WorkerFixtures.homeWithProjects(directory).registering("""
                projects:
                  - name: harness
                    tracker: { kind: edi, projectKey: HRN }
                    forge: { kind: github, path: example-owner/harness, account: example-owner }
                    base: main
                    capacity: 1
                """);
        home.addPlan().add(threeSlices(1, NONE, NONE, CategoryId.IMPLEMENTATION), Actor.PRIMARY);
        WorkerFixtures.dispatchedShip(home, "fit-horn", "HRN-3");

        List<SliceDispatched> whileTheProjectIsFull = home.dispatchEligibleSlices().dispatchWhereThePlansHaveRoom();

        assertThat(whileTheProjectIsFull).isEmpty();
        assertThat(home.dispatches().deferred()).isEmpty();
        assertThat(home.tasks().get(FIT_LAMP).stage()).isEqualTo(TaskStage.QUEUED);
        assertThat(wakesOf(home, WakeKind.SLICE_NOT_DISPATCHED)).isEmpty();
    }

    @Test
    void dispatchesNothingWhileTheCaptainIsAwayAndTheWorkersAreAtTheirCap() throws IOException {
        TestHome home = WorkerFixtures.homeWithProjects(directory);
        home.addPlan().add(threeSlices(2, NONE, NONE, CategoryId.IMPLEMENTATION), Actor.PRIMARY);
        home.presenceStore().save(Presence.presentSince(TestHome.START).away(new Mandate("Carry on with the lamp.", 0), TestHome.START));

        List<SliceDispatched> whileAway = home.dispatchEligibleSlices().dispatchWhereThePlansHaveRoom();

        assertThat(whileAway).isEmpty();
        assertThat(home.dispatches().deferred()).isEmpty();
        assertThat(wakesOf(home, WakeKind.SLICE_NOT_DISPATCHED)).isEmpty();
    }

    @Test
    void tellsThePrimaryOnceOfASliceItCannotDispatchAndGivesItsPlaceToTheNextEligibleSlice() throws IOException {
        TestHome home = WorkerFixtures.homeWithProjects(directory);
        home.addPlan().add(threeSlices(2, NONE, NONE, new CategoryId("tidying")), Actor.PRIMARY);
        DispatchEligibleSlices eligibleSlices = home.dispatchEligibleSlices();

        List<SliceDispatched> firstRound = eligibleSlices.dispatchWhereThePlansHaveRoom();
        delivers(home, FIT_LAMP);
        List<SliceDispatched> secondRound = eligibleSlices.dispatchWhereThePlansHaveRoom();

        assertThat(firstRound).extracting(SliceDispatched::task).containsExactly(FIT_LAMP, OIL_GATE);
        assertThat(secondRound).isEmpty();
        assertThat(wakesOf(home, WakeKind.SLICE_NOT_DISPATCHED)).extracting(Wake::task).containsExactly(PAINT_LAMP);
    }

    private static PlanDraft threeSlices(int capacity, SliceWait paintWaits, SliceWait gateWaits, CategoryId paintCategory) {
        PlanHeading heading = new PlanHeading(WorkKey.parse("HRN-40"), "Light the harbour", new ProjectName("harness"), "docs/research/HRN-40-plan.md",
                "D44", capacity, null);
        return new PlanDraft(heading, List.of(
                new SliceDraft(1, "Fit the lamp", CategoryId.IMPLEMENTATION, WorkKey.parse("HRN-41"), "Use the brass fittings.", NONE),
                new SliceDraft(2, "Paint the lamp", paintCategory, WorkKey.parse("HRN-42"), "", paintWaits),
                new SliceDraft(3, "Oil the gate", CategoryId.IMPLEMENTATION, WorkKey.parse("HRN-43"), "", gateWaits)));
    }

    private static void delivers(TestHome home, TaskId slice) throws IOException {
        Worker worker = home.workers().latestOf(slice).orElseThrow();
        WorkerFixtures.opensTheDraftOf(home, worker);
        home.recordStatus().report(worker.id(), StatusState.DONE, null, null, "The lamp is fitted.");
    }

    private static List<Wake> wakesOf(TestHome home, WakeKind kind) {
        return home.wakes().unacknowledged().stream().map(queued -> queued.wake()).filter(wake -> wake.kind() == kind).toList();
    }
}
