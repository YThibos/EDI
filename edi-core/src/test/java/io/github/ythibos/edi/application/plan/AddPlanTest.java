package io.github.ythibos.edi.application.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.plan.Plan;
import io.github.ythibos.edi.domain.plan.PlanDraft;
import io.github.ythibos.edi.domain.plan.PlanHeading;
import io.github.ythibos.edi.domain.plan.SliceDraft;
import io.github.ythibos.edi.domain.plan.SliceStanding;
import io.github.ythibos.edi.domain.plan.SliceWait;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.AwaitedStage;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskStage;
import io.github.ythibos.edi.domain.task.Wait;
import io.github.ythibos.edi.testing.TestHome;

@Tag("adapter")
class AddPlanTest {

    private static final String REGISTRY = """
            projects:
              - name: harness
                tracker: { kind: edi, projectKey: HRN }
                capacity: 2
            """;
    private static final TaskId FIT_LAMP = new TaskId("fit-the-lamp");
    private static final TaskId PAINT_LAMP = new TaskId("paint-the-lamp");
    private static final TaskId OIL_GATE = new TaskId("oil-the-gate");
    private static final TaskId PLAN_THE_LAMP = new TaskId("plan-work-on-fit-the-lamp");

    @TempDir
    private Path directory;

    @Test
    void queuesEachSliceAsAShipOnItsOwnKeyAndKeepsThePlan() throws IOException {
        TestHome home = TestHome.in(directory).registering(REGISTRY);

        Plan added = home.addPlan().add(threeSlices(2), Actor.PRIMARY);

        assertThat(added.tasks()).containsExactly(FIT_LAMP, PAINT_LAMP, OIL_GATE);
        assertThat(home.plans().get(added.key())).isEqualTo(added);
        Task fitLamp = home.tasks().get(FIT_LAMP);
        assertThat(fitLamp.intake().kind()).isEqualTo(TaskKind.SHIP);
        assertThat(fitLamp.intake().projects()).containsExactly(new ProjectName("harness"));
        assertThat(fitLamp.intake().workKey()).isEqualTo(WorkKey.parse("HRN-41"));
        assertThat(fitLamp.notes()).isEqualTo("Use the brass fittings.");
        assertThat(home.eventTypesOf(PAINT_LAMP)).containsExactly("TaskAdded", "SlicePlanned");
    }

    @Test
    void holdsASliceBackFromDispatchUntilTheSliceItWaitsForIsMerged() throws IOException {
        TestHome home = TestHome.in(directory).registering(REGISTRY);
        home.addPlan().add(threeSlices(2), Actor.PRIMARY);

        List<Wait> beforeTheMerge = home.tasks().waitsOf(home.tasks().get(PAINT_LAMP));
        List<TaskId> readyBefore = readyIn(home);
        home.putAt(FIT_LAMP, TaskStage.MERGED);

        assertThat(beforeTheMerge).containsExactly(new Wait(FIT_LAMP, AwaitedStage.MERGED, TaskStage.QUEUED));
        assertThat(readyBefore).containsExactly(FIT_LAMP, OIL_GATE);
        assertThat(readyIn(home)).containsExactly(PAINT_LAMP, OIL_GATE);
    }

    @Test
    void showsASliceEligibleOnlyOnceEveryWaitOfItsTaskIsMet() throws IOException {
        TestHome home = TestHome.in(directory).registering(REGISTRY);
        home.addPlan().add(threeSlices(2), Actor.PRIMARY);

        home.tasks().insert(Task.queued(PLAN_THE_LAMP, new Intake(TaskKind.SCOUT, "Plan the lamp", List.of(new ProjectName("harness")), null, null,
                List.of(), null), "", TestHome.START));
        home.tasks().update(home.tasks().get(FIT_LAMP).waitingFor(PLAN_THE_LAMP));

        assertThat(eligibleIn(home)).containsExactly(OIL_GATE);
    }

    @Test
    void refusesMoreSlicesAtOnceThanItsProjectRunsAndASecondPlanOnTheSameKey() throws IOException {
        TestHome home = TestHome.in(directory).registering(REGISTRY);

        assertThatThrownBy(() -> home.addPlan().add(threeSlices(3), Actor.PRIMARY)).isInstanceOf(Refusal.class)
                .hasMessage("The plan HRN-40 would work on 3 slices at once, and harness runs at most 2 workers at a time.");
        home.addPlan().add(threeSlices(2), Actor.PRIMARY);
        assertThatThrownBy(() -> home.addPlan().add(threeSlices(1), Actor.PRIMARY)).isInstanceOf(Refusal.class)
                .hasMessage("A plan is already known by HRN-40; edi plan show HRN-40 prints it.");
        assertThat(home.tasks().ids()).containsExactly(FIT_LAMP, PAINT_LAMP, OIL_GATE);
    }

    private static PlanDraft threeSlices(int capacity) {
        PlanHeading heading = new PlanHeading(WorkKey.parse("HRN-40"), "Light the harbour", new ProjectName("harness"), "docs/research/HRN-40-plan.md",
                "D44", capacity, null);
        return new PlanDraft(heading, List.of(
                new SliceDraft(1, "Fit the lamp", CategoryId.IMPLEMENTATION, WorkKey.parse("HRN-41"), "Use the brass fittings.", null),
                new SliceDraft(2, "Paint the lamp", CategoryId.IMPLEMENTATION, WorkKey.parse("HRN-42"), "", null),
                new SliceDraft(3, "Oil the gate", CategoryId.MECHANICAL, WorkKey.parse("HRN-43"), "", new SliceWait.None())));
    }

    private static List<TaskId> readyIn(TestHome home) {
        return home.backlog().backlog(true).entries().stream().map(entry -> entry.task().id()).toList();
    }

    private static List<TaskId> eligibleIn(TestHome home) {
        return home.planQuery().standings(WorkKey.parse("HRN-40")).slices().stream()
                .filter(SliceStanding::isEligible)
                .map(standing -> standing.task().id())
                .toList();
    }
}
