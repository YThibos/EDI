package io.github.ythibos.edi.domain.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.project.ProjectName;

class TaskTest {

    private static final Instant AT = Instant.parse("2026-10-15T09:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-16T09:00:00Z");
    private static final CaptainHold DEFERRAL = new CaptainHold(HoldKind.DEFERRAL, "after the season", HoldUntil.day(LocalDate.of(2026, 11, 1)), AT);
    private static final CaptainsWord DROP = new CaptainsWord("Drop it, we fit no lamps this year.");
    private static final CaptainHold DECISION = new CaptainHold(HoldKind.DECISION, "D58 Which lamp?", HoldUntil.captainsWord(), AT);
    private static final StageTrigger.DoneReported DONE = new StageTrigger.DoneReported(List.of(), List.of(), List.of(), false, List.of(), true);

    @Test
    void entersTheQueueWithItsNotes() {
        Task task = ship().noted("First paragraph.").noted("  Second paragraph.  ");

        assertThat(task.state()).isEqualTo(TaskState.QUEUED);
        assertThat(task.notes()).isEqualTo("Intake notes.\n\nFirst paragraph.\n\nSecond paragraph.");
        assertThat(task.created()).isEqualTo(AT);
    }

    @Test
    void refusesAnEmptyNote() {
        assertThatIllegalArgumentException().isThrownBy(() -> ship().noted("  \n "));
    }

    @Test
    void waitsForTheCaptainUntilReleased() {
        Task held = ship().held(DEFERRAL);

        assertThat(held.isHeld()).isTrue();
        assertThat(held.released().isHeld()).isFalse();
    }

    @Test
    void refusesASecondHold() {
        Task held = ship().held(DEFERRAL);

        assertThatThrownBy(() -> held.held(DEFERRAL)).isInstanceOf(Refusal.class).hasMessageContaining("already held: after the season");
    }

    @Test
    void takesADecisionHoldOnlyFromItsDecision() {
        assertThatThrownBy(() -> ship().held(DECISION)).isInstanceOf(Refusal.class).hasMessageContaining("raised with its card");
    }

    @Test
    void letsADecisionReplaceADeferral() {
        assertThat(ship().held(DEFERRAL).heldForDecision(DECISION).hold()).isEqualTo(DECISION);
    }

    @Test
    void releasesADecisionHoldOnlyThroughTheAnswer() {
        Task waiting = ship().heldForDecision(DECISION);

        assertThatThrownBy(waiting::released).isInstanceOf(Refusal.class).hasMessageContaining("answering the decision releases it");
        assertThat(waiting.decisionAnswered().isHeld()).isFalse();
        assertThat(waiting.decisionAnswered().stage()).isEqualTo(TaskStage.QUEUED);
        assertThat(waiting.decisionAnswered().closesOnItsAnswer()).isFalse();
    }

    @Test
    void retiresACaptainItemThroughTheAnswerToItsDecision() {
        Task item = captainItem().heldForDecision(DECISION).decisionAnswered();

        Task decided = moved(item, new StageTrigger.CaptainItemAnswered(Holds.none()));

        assertThat(item.closesOnItsAnswer()).isTrue();
        assertThat(decided.stage()).isEqualTo(TaskStage.RETIRED);
        assertThat(decided.state()).isEqualTo(TaskState.DONE);
        assertThat(decided.closed()).isEqualTo(LATER);
        assertThat(decided.isHeld()).isFalse();
    }

    @Test
    void refusesToHoldOrReleaseWhatCannotWait() {
        Task retired = moved(captainItem().heldForDecision(DECISION).decisionAnswered(), new StageTrigger.CaptainItemAnswered(Holds.none()));

        assertThatThrownBy(() -> retired.held(DEFERRAL)).isInstanceOf(Refusal.class).hasMessageContaining("is retired");
        assertThatThrownBy(() -> retired.heldForDecision(DECISION)).isInstanceOf(Refusal.class).hasMessageContaining("is retired");
        assertThatThrownBy(ship()::released).isInstanceOf(Refusal.class).hasMessageContaining("is not held");
        assertThatThrownBy(() -> ship().decisionAnswered()).isInstanceOf(Refusal.class).hasMessageContaining("not held for a decision");
    }

    @Test
    void dropsFromEveryOpenStageOnTheCaptainsWord() {
        Task working = moved(ship(), dispatch());

        for (Task task : List.of(ship(), ship().held(DEFERRAL), ship().heldForDecision(DECISION), working, working.held(DEFERRAL))) {
            Task dropped = moved(task, drop());

            assertThat(dropped.stage()).isEqualTo(TaskStage.DROPPED);
            assertThat(dropped.state()).isEqualTo(TaskState.DROPPED);
            assertThat(dropped.closed()).isEqualTo(LATER);
            assertThat(dropped.isHeld()).isFalse();
        }
    }

    @Test
    void neverDropsWhatIsRetired() {
        Task retired = moved(captainItem().heldForDecision(DECISION).decisionAnswered(), new StageTrigger.CaptainItemAnswered(Holds.none()));

        assertThatThrownBy(() -> moved(retired, drop())).isInstanceOf(Refusal.class).hasMessageContaining("lamp-choice is retired");
    }

    @Test
    void dropsOnlyOnTheCaptainsWord() {
        assertThatThrownBy(() -> new StageTrigger.Drop(null, List.of(), false)).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("captain's word");
        assertThatIllegalArgumentException().isThrownBy(() -> new CaptainsWord("  "));
    }

    @Test
    void waitsForNobodyOnceDropped() {
        Task dropped = moved(ship(), drop());

        assertThatThrownBy(() -> moved(dropped, drop())).isInstanceOf(Refusal.class).hasMessageContaining("is dropped");
        assertThatThrownBy(() -> dropped.held(DEFERRAL)).isInstanceOf(Refusal.class).hasMessageContaining("is dropped");
        assertThatThrownBy(() -> dropped.heldForDecision(DECISION)).isInstanceOf(Refusal.class).hasMessageContaining("is dropped");
    }

    @Test
    void retiresOnceItsLandedWorkIsCleanedUp() {
        Task merged = moved(moved(moved(ship(), dispatch()), DONE), new StageTrigger.RequestMerged("7e23dd8", List.of()));
        Task landed = moved(merged, new StageTrigger.LandingObserved(true, List.of()));

        Task cleaned = moved(landed, new StageTrigger.CleanUp(Set.of(), false));

        assertThat(List.of(merged.stage(), landed.stage(), cleaned.stage())).containsExactly(TaskStage.MERGED, TaskStage.LANDED, TaskStage.RETIRED);
        assertThat(List.of(merged.state(), landed.state(), cleaned.state())).containsExactly(TaskState.IN_FLIGHT, TaskState.IN_FLIGHT, TaskState.DONE);
        assertThat(landed.hasLanded()).isTrue();
        assertThat(cleaned.hasLanded()).isTrue();
        assertThat(cleaned.closed()).isEqualTo(LATER);
    }

    @Test
    void isReadyForItsCleanUpOnlyInFlightAndFreeOfHolds() {
        Task working = moved(ship(), dispatch());

        assertThatThrownBy(() -> ship().requireReadyForCleanUp()).isInstanceOf(Refusal.class)
                .hasMessage("fit-lamp is queued, and only work in flight is cleaned up.");
        assertThatThrownBy(() -> working.held(DEFERRAL).requireReadyForCleanUp()).isInstanceOf(Refusal.class)
                .hasMessage("fit-lamp is held: after the season. Release it before cleaning it up.");
    }

    @Test
    void takesOnlyAChangeFromTheStageItIsAt() {
        StageChange fromQueued = ship().transitions().move(TaskStage.QUEUED, dispatch());
        Task working = ship().movedBy(fromQueued, LATER);
        Task otherShip = Task.queued(new TaskId("fit-horn"), ship().intake(), "", AT);

        assertThatThrownBy(() -> working.movedBy(fromQueued, LATER)).isInstanceOf(Refusal.class)
                .hasMessage("fit-lamp is working now, no longer queued, so it does not become working.");
        assertThatIllegalArgumentException().isThrownBy(() -> otherShip.movedBy(fromQueued, LATER));
    }

    @Test
    void isFreeToWorkOnlyAsAnUnheldShipOrScout() {
        assertThatThrownBy(() -> captainItem().requireFreeToWork()).isInstanceOf(Refusal.class)
                .hasMessage("lamp-choice is a captain item; only ships and scouts get a worker.");
        assertThatThrownBy(() -> ship().held(DEFERRAL).requireFreeToWork()).isInstanceOf(Refusal.class)
                .hasMessage("fit-lamp is held: after the season. Release it before dispatching it.");
        assertThatThrownBy(() -> moved(ship(), drop()).requireFreeToWork()).isInstanceOf(Refusal.class).hasMessageContaining("is dropped");
    }

    @Test
    void followsItsProjectsDeliveryUnlessTheCaptainAskedForAnotherOne() {
        Intake ownWay = new Intake(TaskKind.SHIP, "Fit", List.of(new ProjectName("harness")), null, null, List.of(), DeliveryMode.LOCAL_APPLY);

        assertThat(ship().intake().deliveryOr(DeliveryMode.DRAFT_REVIEW)).isEqualTo(DeliveryMode.DRAFT_REVIEW);
        assertThat(ownWay.deliveryOr(DeliveryMode.DRAFT_REVIEW)).isEqualTo(DeliveryMode.LOCAL_APPLY);
        assertThat(DeliveryMode.registryDefault("local-branch")).isEqualTo(DeliveryMode.LOCAL_BRANCH);
        assertThatIllegalArgumentException().isThrownBy(() -> DeliveryMode.registryDefault("local-apply"))
                .withMessageContaining("never a project's default");
        assertThatIllegalArgumentException().isThrownBy(() -> DeliveryMode.registryDefault("report"))
                .withMessage("report is a delivery the captain asks for on one task, never a project's default; give the task --delivery report instead.");
    }

    @Test
    void keepsItsStateConsistent() {
        Intake intake = ship().intake();
        TaskId id = new TaskId("fit-lamp");

        assertThatIllegalArgumentException().isThrownBy(() -> new Task(id, intake, TaskStage.RETIRED, "", null, AT, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Task(id, intake, TaskStage.QUEUED, "", null, AT, LATER));
        assertThatIllegalArgumentException().isThrownBy(() -> new Task(id, intake, TaskStage.MERGED, "", null, AT, LATER));
        assertThatIllegalArgumentException().isThrownBy(() -> new Task(id, intake, TaskStage.RETIRED, "", DEFERRAL, AT, LATER));
        assertThatIllegalArgumentException().isThrownBy(() -> new Task(id, intake, TaskStage.DROPPED, "", null, AT, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Task(id, intake, TaskStage.DROPPED, "", DEFERRAL, AT, LATER));
        assertThatIllegalArgumentException().isThrownBy(() -> new Task(id,
                new Intake(TaskKind.SHIP, "Fit", List.of(), null, null, List.of(id), null), TaskStage.QUEUED, "", null, AT, null));
    }

    @Test
    void stopsWaitingForATaskThatClosedAndKeepsWaitingForTheOthers() {
        TaskId cleanup = new TaskId("refuse-cleanup");
        TaskId drill = new TaskId("choose-drill");
        Task waiting = Task.queued(new TaskId("plan-timesheet"),
                new Intake(TaskKind.SHIP, "Plan the timesheet", List.of(new ProjectName("harness")), null, null, List.of(cleanup, drill), null), "", AT);

        assertThat(waiting.noLongerWaitingFor(cleanup).intake().blockedBy()).containsExactly(drill);
        assertThat(waiting.noLongerWaitingFor(cleanup).noLongerWaitingFor(drill).intake().blockedBy()).isEmpty();
    }

    private static Task moved(Task task, StageTrigger trigger) {
        return task.movedBy(task.transitions().move(task.stage(), trigger), LATER);
    }

    private static StageTrigger.Dispatch dispatch() {
        return new StageTrigger.Dispatch(Holds.none(), List.of(), true);
    }

    private static StageTrigger.Drop drop() {
        return new StageTrigger.Drop(DROP, List.of(), false);
    }

    private static Task captainItem() {
        return Task.queued(new TaskId("lamp-choice"), new Intake(TaskKind.CAPTAIN, "Choose the lamp", List.of(), null, null, List.of(), null), "", AT);
    }

    private static Task ship() {
        return Task.queued(new TaskId("fit-lamp"),
                new Intake(TaskKind.SHIP, "Fit the lamp", List.of(new ProjectName("harness")), null, null, List.of(), null), "  Intake notes. ", AT);
    }
}
