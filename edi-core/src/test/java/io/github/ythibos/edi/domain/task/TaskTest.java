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
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.project.ProjectName;

class TaskTest {

    private static final Instant AT = Instant.parse("2026-10-15T09:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-16T09:00:00Z");
    private static final Hold.Deferral DEFERRAL = new Hold.Deferral("after the season", HoldUntil.day(LocalDate.of(2026, 11, 1)), AT);
    private static final CaptainsWord DROP = new CaptainsWord("Drop it, we fit no lamps this year.");
    private static final DecisionNumber D58 = new DecisionNumber(58);
    private static final DecisionNumber D59 = new DecisionNumber(59);
    private static final Hold.OpenDecision DECISION = new Hold.OpenDecision(D58, "Which lamp?", AT);
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
    void refusesASecondHoldOfTheSameKind() {
        Task held = ship().held(DEFERRAL);

        assertThatThrownBy(() -> held.held(DEFERRAL)).isInstanceOf(Refusal.class).hasMessageContaining("already waits: deferred: after the season");
    }

    @Test
    void keepsADeferralBesideADecisionAndAfterItsAnswer() {
        Task waiting = ship().held(DEFERRAL).held(DECISION);

        assertThat(waiting.holds().held()).containsExactly(DEFERRAL, DECISION);
        assertThat(waiting.decisionAnswered(D58).holds().held()).containsExactly(DEFERRAL);
    }

    @Test
    void waitsOnTwoDecisionsAndLetsEachAnswerReleaseOnlyItsOwn() {
        Task waiting = ship().held(DECISION).held(new Hold.OpenDecision(D59, "Which bulb?", LATER));

        assertThat(waiting.decisionAnswered(D58).holds().openDecisions()).containsExactly(D59);
        assertThat(waiting.decisionAnswered(D58).reasonsItWaits()).isEqualTo("D59 waits for its answer");
    }

    @Test
    void releasesADecisionHoldOnlyThroughTheAnswer() {
        Task waiting = ship().held(DECISION).held(new Hold.OpenDecision(D59, "Which bulb?", LATER));

        assertThatThrownBy(waiting::released).isInstanceOf(Refusal.class)
                .hasMessage("fit-lamp waits only on D58 and D59; answering a decision releases it.");
        assertThat(waiting.decisionAnswered(D58).decisionAnswered(D59).isHeld()).isFalse();
        assertThat(waiting.decisionAnswered(D58).stage()).isEqualTo(TaskStage.QUEUED);
        assertThat(waiting.closesOnItsAnswer()).isFalse();
    }

    @Test
    void releasesEveryHoldButItsDecisionsOnTheCaptainsRelease() {
        Hold.Outage lost = new Hold.Outage(2, "lost when the machine restarted", AT);
        Task waiting = ship().held(DEFERRAL).held(DECISION).held(lost);

        assertThat(waiting.released().holds().held()).containsExactly(DECISION);
        assertThat(waiting.releasedFrom(lost).holds().held()).containsExactly(DEFERRAL, DECISION);
    }

    @Test
    void retiresACaptainItemThroughTheAnswerToItsDecision() {
        Task item = captainItem().held(DECISION).decisionAnswered(D58);

        Task decided = moved(item, new StageTrigger.CaptainItemAnswered(Holds.none()));

        assertThat(item.closesOnItsAnswer()).isTrue();
        assertThat(decided.stage()).isEqualTo(TaskStage.RETIRED);
        assertThat(decided.state()).isEqualTo(TaskState.DONE);
        assertThat(decided.closed()).isEqualTo(LATER);
        assertThat(decided.isHeld()).isFalse();
    }

    @Test
    void refusesToHoldOrReleaseWhatCannotWait() {
        Task retired = moved(captainItem().held(DECISION).decisionAnswered(D58), new StageTrigger.CaptainItemAnswered(Holds.none()));

        assertThatThrownBy(() -> retired.held(DEFERRAL)).isInstanceOf(Refusal.class).hasMessageContaining("is retired");
        assertThatThrownBy(() -> retired.held(DECISION)).isInstanceOf(Refusal.class).hasMessageContaining("is retired");
        assertThatThrownBy(ship()::released).isInstanceOf(Refusal.class).hasMessageContaining("is not held");
        assertThatThrownBy(() -> ship().decisionAnswered(D58)).isInstanceOf(Refusal.class).hasMessage("The task does not wait: D58 waits for its answer.");
    }

    @Test
    void dropsFromEveryOpenStageOnTheCaptainsWord() {
        Task working = moved(ship(), dispatch());

        for (Task task : List.of(ship(), ship().held(DEFERRAL), ship().held(DECISION), working, working.held(DEFERRAL))) {
            Task dropped = moved(task, drop());

            assertThat(dropped.stage()).isEqualTo(TaskStage.DROPPED);
            assertThat(dropped.state()).isEqualTo(TaskState.DROPPED);
            assertThat(dropped.closed()).isEqualTo(LATER);
            assertThat(dropped.isHeld()).isFalse();
        }
    }

    @Test
    void neverDropsWhatIsRetired() {
        Task retired = moved(captainItem().held(DECISION).decisionAnswered(D58), new StageTrigger.CaptainItemAnswered(Holds.none()));

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
        assertThatThrownBy(() -> dropped.held(DECISION)).isInstanceOf(Refusal.class).hasMessageContaining("is dropped");
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
                .hasMessage("fit-lamp is held: deferred: after the season. Release it before cleaning it up.");
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
                .hasMessage("fit-lamp is held: deferred: after the season. Release it before dispatching it.");
        assertThatThrownBy(() -> moved(ship(), drop()).requireFreeToWork()).isInstanceOf(Refusal.class).hasMessageContaining("is dropped");
    }

    @Test
    void followsItsProjectsDeliveryUnlessTheCaptainAskedForAnotherOne() {
        Intake ownWay = new Intake(TaskKind.SHIP, "Fit", List.of(new ProjectName("harness")), null, null, List.of(), DeliveryMode.LOCAL_APPLY, null);

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

        assertThatIllegalArgumentException().isThrownBy(() -> new Task(id, intake, TaskStage.RETIRED, "", Holds.none(), AT, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Task(id, intake, TaskStage.QUEUED, "", Holds.none(), AT, LATER));
        assertThatIllegalArgumentException().isThrownBy(() -> new Task(id, intake, TaskStage.MERGED, "", Holds.none(), AT, LATER));
        assertThatIllegalArgumentException().isThrownBy(() -> new Task(id, intake, TaskStage.RETIRED, "", Holds.none().with(DEFERRAL), AT, LATER));
        assertThatIllegalArgumentException().isThrownBy(() -> new Task(id, intake, TaskStage.DROPPED, "", Holds.none(), AT, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Task(id, intake, TaskStage.DROPPED, "", Holds.none().with(DEFERRAL), AT, LATER));
        assertThatIllegalArgumentException().isThrownBy(() -> new Task(id,
                new Intake(TaskKind.SHIP, "Fit", List.of(), null, null, List.of(id), null, null), TaskStage.QUEUED, "", Holds.none(), AT, null));
    }

    @Test
    void stopsWaitingForATaskThatClosedAndKeepsWaitingForTheOthers() {
        TaskId cleanup = new TaskId("refuse-cleanup");
        TaskId drill = new TaskId("choose-drill");
        Task waiting = Task.queued(new TaskId("plan-timesheet"),
                new Intake(TaskKind.SHIP, "Plan the timesheet", List.of(new ProjectName("harness")), null, null, List.of(cleanup, drill), null, null), "", AT);

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
        return Task.queued(new TaskId("lamp-choice"), new Intake(TaskKind.CAPTAIN, "Choose the lamp", List.of(), null, null, List.of(), null, null), "", AT);
    }

    private static Task ship() {
        return Task.queued(new TaskId("fit-lamp"),
                new Intake(TaskKind.SHIP, "Fit the lamp", List.of(new ProjectName("harness")), null, null, List.of(), null, null), "  Intake notes. ", AT);
    }
}
