package io.github.ythibos.edi.application.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.UnplannedShip;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.testing.ExampleHome;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class ResearchBeforeImplementationTest {

    private static final TaskId LAMP = new TaskId("fit-lamp");
    private static final TaskId PLAN = new TaskId("plan-work-on-fit-lamp");

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void switchResearchFirstOn() throws IOException {
        home = WorkerFixtures.homeWithProjects(directory)
                .writing("config/dispatch-rules.json", ExampleHome.file("config/dispatch-rules.json").replace("\"researchFirst\": false", "\"researchFirst\": true"));
        home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));
    }

    @Test
    void plansUnplannedWorkInAResearchTaskAndQueuesTheShipBehindIt() {
        DispatchOutcome outcome = dispatch("fit-lamp", "implementation-unplanned", "Use the brass fittings.", false);

        assertThat(outcome.plannedShip()).isEqualTo(LAMP);
        assertThat(outcome.launched().worker().id().task()).isEqualTo(PLAN);
        assertThat(outcome.launched().worker().category()).isEqualTo(CategoryId.RESEARCH);
        assertThat(outcome.launched().branch()).hasToString("research/HRN-3");
        Task plan = home.tasks().get(PLAN);
        assertThat(plan.intake().kind()).isEqualTo(TaskKind.SCOUT);
        assertThat(plan.intake().title()).isEqualTo("Plan: Work on fit-lamp");
        assertThat(plan.intake().workKey()).isEqualTo(WorkKey.parse("HRN-3"));
        assertThat(plan.notes()).isEqualTo("Do the work on fit-lamp.");
        Task ship = home.tasks().get(LAMP);
        assertThat(ship.state()).isEqualTo(TaskState.QUEUED);
        assertThat(ship.intake().blockedBy()).containsExactly(PLAN);
        assertThat(home.dispatches().of(LAMP).orElseThrow().category()).isEqualTo(CategoryId.IMPLEMENTATION);
        assertThat(home.unplannedShips().of(LAMP)).contains(new UnplannedShip(LAMP, CategoryId.IMPLEMENTATION_UNPLANNED, PLAN, TestHome.START));
        assertThat(home.eventTypesOf(LAMP)).containsExactly("ResearchQueuedFirst");
        assertThat(home.dispatches().of(PLAN).orElseThrow().spec())
                .startsWith("Research before implementation: plan the ship `fit-lamp`")
                .endsWith("The ship's own build instructions, which the plan refines:\n\nUse the brass fittings.");
    }

    @Test
    void dispatchesThePlannedShipAsImplementationWithThePlanInItsBrief() throws IOException {
        dispatch("fit-lamp", "implementation-unplanned", "Use the brass fittings.", false);
        assertThatThrownBy(() -> dispatch("fit-lamp", null, "", false)).isInstanceOf(Refusal.class)
                .hasMessage("fit-lamp waits for plan-work-on-fit-lamp to land; it is dispatched once each wait is met.");

        home.markDone(PLAN);
        assertThatThrownBy(() -> dispatch("fit-lamp", null, "", false)).isInstanceOf(Refusal.class)
                .hasMessageStartingWith("The plan of plan-work-on-fit-lamp is not written yet:");

        Files.writeString(home.home().taskDirectory(PLAN).resolve("report.md"), "1. Fit the brass lamp in the hall.\n2. Test that it lights.\n");
        DispatchOutcome ship = dispatch("fit-lamp", null, "", false);

        assertThat(ship.plansAShip()).isFalse();
        assertThat(ship.launched().worker().category()).isEqualTo(CategoryId.IMPLEMENTATION);
        assertThat(ship.launched().branch()).hasToString("feature/HRN-3");
        assertThat(Files.readString(home.home().taskDirectory(LAMP).resolve("brief.md"))).contains("""
                Follow the plan that research task `plan-work-on-fit-lamp` settled; its report follows verbatim.

                1. Fit the brass lamp in the hall.
                2. Test that it lights.

                Use the brass fittings.""");
    }

    @Test
    void plansABugOnlyWhenItsCauseIsUnknown() {
        home.storing(List.of(WorkerFixtures.queuedShip("fix-horn", "harness", "HRN-4"), WorkerFixtures.queuedShip("fix-bell", "harness", "HRN-5")));

        assertThat(dispatch("fit-lamp", "bugfix", "", false).plansAShip()).isFalse();
        assertThat(dispatch("fix-horn", "bugfix", "", true).plannedShip()).isEqualTo(new TaskId("fix-horn"));
        assertThatThrownBy(() -> dispatch("fix-bell", "implementation", "", true)).isInstanceOf(Refusal.class)
                .hasMessage("Only a bugfix dispatch says its cause is unknown; implementation work is planned or not.");
    }

    @Test
    void leavesTheShipUntouchedWhenItsResearchScoutCannotBeDispatched() throws IOException {
        home.registering("""
                projects:
                  - name: harness
                    tracker: { kind: edi, projectKey: HRN }
                    forge: { kind: github, path: example-owner/harness, account: example-owner }
                    base: main
                    branchKinds: [feature, chore]
                """);

        assertThatThrownBy(() -> dispatch("fit-lamp", "implementation-unplanned", "", false)).isInstanceOf(Refusal.class)
                .hasMessage("harness takes no research branches; its registry entry allows [chore, feature].");

        Task ship = home.tasks().get(LAMP);
        assertThat(ship.intake().blockedBy()).isEmpty();
        assertThat(home.tasks().contains(PLAN)).isFalse();
        assertThat(home.dispatches().of(LAMP)).isEmpty();
        assertThat(home.unplannedShips().of(LAMP)).isEmpty();
        assertThat(home.eventTypesOf(LAMP)).isEmpty();
    }

    @Test
    void leavesTheShipUntouchedWhenOnlyItsResearchScoutsBriefRunsPastItsLength() throws IOException {
        home.registering("""
                projects:
                  - name: harness
                    tracker: { kind: edi, projectKey: HRN }
                    base: main
                    delivery: local-branch
                """);
        String spec = "Build instruction.\n".repeat(41).strip();

        assertThatThrownBy(() -> dispatch("fit-lamp", "implementation-unplanned", spec, false)).isInstanceOf(Refusal.class)
                .hasMessageContaining("its longest section is Spec");

        assertThat(home.tasks().contains(PLAN)).isFalse();
        assertThat(home.tasks().get(LAMP).intake().blockedBy()).isEmpty();
        assertThat(home.dispatches().of(LAMP)).isEmpty();
        assertThat(home.unplannedShips().of(LAMP)).isEmpty();
        assertThat(home.eventTypesOf(LAMP)).isEmpty();
        home.writing("config/dispatch-rules.json", ExampleHome.file("config/dispatch-rules.json"));
        assertThat(dispatch("fit-lamp", "implementation-unplanned", spec, false).launched().worker().category())
                .isEqualTo(CategoryId.IMPLEMENTATION_UNPLANNED);
    }

    @Test
    void leavesTheEdiProjectOutOfThePilot() {
        home.storing(List.of(WorkerFixtures.queuedShip("tune-core", "EDI", "EDI-20")));

        DispatchOutcome outcome = dispatch("tune-core", "implementation-unplanned", "", false);

        assertThat(outcome.plansAShip()).isFalse();
        assertThat(outcome.launched().worker().category()).isEqualTo(CategoryId.IMPLEMENTATION_UNPLANNED);
        assertThat(home.unplannedShips().of(new TaskId("tune-core"))).isEmpty();
    }

    @Test
    void countsUnplannedWorkForThePilotWhileTheSwitchIsOff() throws IOException {
        home.writing("config/dispatch-rules.json", ExampleHome.file("config/dispatch-rules.json"));

        DispatchOutcome outcome = dispatch("fit-lamp", "implementation-unplanned", "", false);

        assertThat(outcome.launched().worker().category()).isEqualTo(CategoryId.IMPLEMENTATION_UNPLANNED);
        assertThat(home.unplannedShips().of(LAMP)).contains(new UnplannedShip(LAMP, CategoryId.IMPLEMENTATION_UNPLANNED, null, TestHome.START));
    }

    private DispatchOutcome dispatch(String task, String category, String spec, boolean causeUnknown) {
        return home.dispatchTask().dispatch(TaskReference.parse(task),
                new DispatchRequest(category == null ? null : new CategoryId(category), spec, List.of(), null, causeUnknown),
                        CallingSession.UNNAMED, Actor.PRIMARY);
    }
}
