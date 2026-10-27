package io.github.ythibos.edi.domain.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.landing.BranchCommit;
import io.github.ythibos.edi.domain.task.Hold.Deferral;
import io.github.ythibos.edi.domain.task.Hold.OpenDecision;
import io.github.ythibos.edi.domain.task.StageTrigger.CaptainItemAnswered;
import io.github.ythibos.edi.domain.task.StageTrigger.Change;
import io.github.ythibos.edi.domain.task.StageTrigger.CleanUp;
import io.github.ythibos.edi.domain.task.StageTrigger.Dispatch;
import io.github.ythibos.edi.domain.task.StageTrigger.DoneReported;
import io.github.ythibos.edi.domain.task.StageTrigger.Drop;
import io.github.ythibos.edi.domain.task.StageTrigger.ForgeObserved;
import io.github.ythibos.edi.domain.task.StageTrigger.LandingObserved;
import io.github.ythibos.edi.domain.task.StageTrigger.RequestMerged;

class StageTransitionsTest {

    private static final TaskId LAMP = new TaskId("fit-the-lamp");
    private static final StageTransitions SHIP = new StageTransitions(LAMP, TaskKind.SHIP);
    private static final StageTransitions SCOUT = new StageTransitions(LAMP, TaskKind.SCOUT);
    private static final StageTransitions CAPTAIN_ITEM = new StageTransitions(LAMP, TaskKind.CAPTAIN);
    private static final DecisionNumber D59 = new DecisionNumber(59);
    private static final Deferral AFTER_THE_SEASON = new Deferral("after the season", HoldUntil.day(LocalDate.of(2026, 11, 1)));
    private static final String OLD_HEAD = "7e23dd8";
    private static final BranchCommit THE_STEERS_COMMIT = new BranchCommit("b30e15a", "Apply the amendment", false);
    private static final CaptainsWord DROP_IT = new CaptainsWord("Drop it, we fit no lamps this year.");

    @Nested
    class Dispatching {

        @Test
        void startsAQueuedTaskWorking() {
            StageChange change = SHIP.move(TaskStage.QUEUED, dispatchWithRoom());

            assertThat(change).isEqualTo(new StageChange(LAMP, TaskStage.QUEUED, TaskStage.WORKING, "dispatched", List.of()));
        }

        @Test
        void refusesAHeldTask() {
            Dispatch held = new Dispatch(Holds.none().with(AFTER_THE_SEASON), List.of(), true);

            assertThatThrownBy(() -> SHIP.move(TaskStage.QUEUED, held)).isInstanceOf(Refusal.class)
                    .hasMessage("fit-the-lamp stays queued: deferred: after the season.");
        }

        @Test
        void refusesATaskWhoseWaitIsNotMet() {
            Dispatch waiting = new Dispatch(Holds.none(), List.of(new TaskId("wire-the-socket"), new TaskId("buy-the-bulb")), true);

            assertThatThrownBy(() -> SHIP.move(TaskStage.QUEUED, waiting)).isInstanceOf(Refusal.class)
                    .hasMessageContaining("it waits for wire-the-socket and buy-the-bulb");
        }

        @Test
        void refusesATaskWithoutRoomToWork() {
            Dispatch full = new Dispatch(Holds.none(), List.of(), false);

            assertThatThrownBy(() -> SHIP.move(TaskStage.QUEUED, full)).isInstanceOf(Refusal.class)
                    .hasMessageContaining("its project or plan has no room for another worker");
        }

        @Test
        void refusesACaptainItem() {
            assertThatThrownBy(() -> CAPTAIN_ITEM.move(TaskStage.QUEUED, dispatchWithRoom())).isInstanceOf(Refusal.class)
                    .hasMessageContaining("a captain item gets no worker");
        }

        @Test
        void refusesATaskAlreadyWorking() {
            assertThatThrownBy(() -> SHIP.move(TaskStage.WORKING, dispatchWithRoom())).isInstanceOf(Refusal.class)
                    .hasMessage("fit-the-lamp is working, and a task becomes working only from queued.");
        }
    }

    @Nested
    class Delivering {

        @ParameterizedTest
        @EnumSource(names = {"WORKING", "CHANGING"})
        void deliversAShipWhoseClaimsHold(TaskStage stage) {
            assertThat(SHIP.move(stage, aCleanDone()).to()).isEqualTo(TaskStage.DELIVERED);
        }

        @Test
        void deliversAScoutWithItsReport() {
            DoneReported reported = new DoneReported(List.of(), List.of(), List.of(), false, List.of(), true);

            assertThat(SCOUT.move(TaskStage.WORKING, reported).to()).isEqualTo(TaskStage.DELIVERED);
        }

        @Test
        void refusesAScoutWithoutItsReport() {
            assertThatThrownBy(() -> SCOUT.move(TaskStage.WORKING, aCleanDone())).isInstanceOf(Refusal.class)
                    .hasMessage("fit-the-lamp stays working: its report.md is not filed.");
        }

        @Test
        void refusesADoneWithEveryGapNamed() {
            DoneReported unfinished = new DoneReported(List.of("lamp.txt"), List.of("pom.xml"), List.of(2, 3), true,
                    List.of("the request shows head 1a2b, but the isolated copy is at 3c4d"), false);

            assertThatThrownBy(() -> SHIP.move(TaskStage.WORKING, unfinished)).isInstanceOf(Refusal.class)
                    .hasMessage("fit-the-lamp stays working: lamp.txt is not committed; pom.xml adds a SNAPSHOT version;"
                            + " messages 2 and 3 wait unacknowledged; its pipeline run is still running;"
                            + " the request shows head 1a2b, but the isolated copy is at 3c4d.");
        }

        @Test
        void refusesATaskThatIsNotWorking() {
            assertThatThrownBy(() -> SHIP.move(TaskStage.QUEUED, aCleanDone())).isInstanceOf(Refusal.class)
                    .hasMessage("fit-the-lamp is queued, and a task becomes delivered only from working or changing.");
        }
    }

    @Nested
    class Readying {

        @Test
        void readiesADeliveredRequestOutOfDraftWithAPassedRunAtItsDeliveredHead() {
            assertThat(SHIP.move(TaskStage.DELIVERED, readyOnTheForge()).to()).isEqualTo(TaskStage.READY);
        }

        @Test
        void refusesADraftWhoseRunHasNotPassedAtAnotherHead() {
            assertThatThrownBy(() -> SHIP.move(TaskStage.DELIVERED, new ForgeObserved(true, false, false))).isInstanceOf(Refusal.class)
                    .hasMessage("fit-the-lamp stays delivered: its request is a draft; its latest pipeline run has not passed;"
                            + " its request's head is not the delivered head.");
        }

        @Test
        void refusesAScout() {
            assertThatThrownBy(() -> SCOUT.move(TaskStage.DELIVERED, readyOnTheForge())).isInstanceOf(Refusal.class)
                    .hasMessageContaining("only a ship's request is reviewed on the forge");
        }

        @Test
        void refusesATaskThatIsNotDelivered() {
            assertThatThrownBy(() -> SHIP.move(TaskStage.CHANGING, readyOnTheForge())).isInstanceOf(Refusal.class)
                    .hasMessage("fit-the-lamp is changing, and a task becomes ready only from delivered.");
        }
    }

    @Nested
    class Changing {

        @ParameterizedTest
        @EnumSource(ChangeCause.class)
        void changesADeliveredTaskForEachCause(ChangeCause cause) {
            StageChange change = SHIP.move(TaskStage.DELIVERED, new Change(cause));

            assertThat(change.to()).isEqualTo(TaskStage.CHANGING);
            assertThat(change.reason()).isEqualTo(cause.sentence());
        }

        @ParameterizedTest
        @EnumSource(ChangeCause.class)
        void changesAReadyTaskForEachCause(ChangeCause cause) {
            assertThat(SHIP.move(TaskStage.READY, new Change(cause)).to()).isEqualTo(TaskStage.CHANGING);
        }

        @Test
        void refusesATaskStillWorking() {
            assertThatThrownBy(() -> SHIP.move(TaskStage.WORKING, new Change(ChangeCause.STEER_SENT))).isInstanceOf(Refusal.class)
                    .hasMessage("fit-the-lamp is working, and a task becomes changing only from delivered or ready.");
        }
    }

    @Nested
    class Merging {

        @ParameterizedTest
        @EnumSource(names = {"WORKING", "DELIVERED", "CHANGING", "READY"})
        void recordsTheMergedHead(TaskStage stage) {
            StageChange change = SHIP.move(stage, new RequestMerged(OLD_HEAD, List.of()));

            assertThat(change.to()).isEqualTo(TaskStage.MERGED);
            assertThat(change.reason()).isEqualTo("merged at 7e23dd8");
            assertThat(change.raisesAFollowUpDecision()).isFalse();
        }

        @Test
        void refusesAMergeWithoutItsHead() {
            assertThatIllegalArgumentException().isThrownBy(() -> new RequestMerged(" ", List.of()));
        }

        @ParameterizedTest
        @EnumSource(names = {"QUEUED", "MERGED", "LANDED", "RETIRED", "DROPPED"})
        void refusesATaskWithNoRequestToMerge(TaskStage stage) {
            assertThatThrownBy(() -> SHIP.move(stage, new RequestMerged(OLD_HEAD, List.of()))).isInstanceOf(Refusal.class)
                    .hasMessageEndingWith("a task becomes merged only from working, delivered, changing or ready.");
        }
    }

    @Nested
    class Landing {

        @Test
        void landsAMergeInTheBaseWithNothingAfterItsHead() {
            StageChange change = SHIP.move(TaskStage.MERGED, new LandingObserved(true, List.of()));

            assertThat(change.to()).isEqualTo(TaskStage.LANDED);
        }

        @Test
        void refusesAMergeNotInTheBaseYet() {
            assertThatThrownBy(() -> SHIP.move(TaskStage.MERGED, new LandingObserved(false, List.of()))).isInstanceOf(Refusal.class)
                    .hasMessage("fit-the-lamp stays merged: the commit the merge landed is not in the base yet.");
        }

        @Test
        void refusesATaskThatIsNotMerged() {
            assertThatThrownBy(() -> SHIP.move(TaskStage.READY, new LandingObserved(true, List.of()))).isInstanceOf(Refusal.class)
                    .hasMessage("fit-the-lamp is ready, and a task becomes landed only from merged.");
        }
    }

    @Nested
    class Retiring {

        @Test
        void retiresALandedShipWithNothingLeftBehind() {
            assertThat(SHIP.move(TaskStage.LANDED, new CleanUp(Set.of(), false)).to()).isEqualTo(TaskStage.RETIRED);
        }

        @Test
        void refusesACleanUpThatLeftSomethingBehind() {
            CleanUp partial = new CleanUp(Set.of(Leftover.BOARD, Leftover.COPY), false);

            assertThatThrownBy(() -> SHIP.move(TaskStage.LANDED, partial)).isInstanceOf(Refusal.class)
                    .hasMessage("fit-the-lamp stays landed: its isolated copy is not retired; a board is not retired.");
        }

        @Test
        void refusesAShipThatHasNotLanded() {
            assertThatThrownBy(() -> SHIP.move(TaskStage.MERGED, new CleanUp(Set.of(), true))).isInstanceOf(Refusal.class)
                    .hasMessage("fit-the-lamp is merged, and a task becomes retired only from landed.");
        }

        @Test
        void retiresADeliveredScoutWithItsReport() {
            assertThat(SCOUT.move(TaskStage.DELIVERED, new CleanUp(Set.of(), true)).to()).isEqualTo(TaskStage.RETIRED);
        }

        @Test
        void refusesAScoutWithoutItsReport() {
            assertThatThrownBy(() -> SCOUT.move(TaskStage.DELIVERED, new CleanUp(Set.of(), false))).isInstanceOf(Refusal.class)
                    .hasMessage("fit-the-lamp stays delivered: its report.md is not filed.");
        }

        @Test
        void refusesAScoutThatHasNotDelivered() {
            assertThatThrownBy(() -> SCOUT.move(TaskStage.WORKING, new CleanUp(Set.of(), true))).isInstanceOf(Refusal.class)
                    .hasMessage("fit-the-lamp is working, and a task becomes retired only from delivered.");
        }
    }

    @Nested
    class ClosingACaptainItem {

        @Test
        void retiresAnItemOnceItsDecisionIsAnswered() {
            StageChange change = CAPTAIN_ITEM.move(TaskStage.QUEUED, new CaptainItemAnswered(Holds.none()));

            assertThat(change.to()).isEqualTo(TaskStage.RETIRED);
            assertThat(change.reason()).isEqualTo("its decision is answered");
        }

        @Test
        void refusesWhileAnotherDecisionIsOpen() {
            CaptainItemAnswered stillOpen = new CaptainItemAnswered(Holds.none().with(new OpenDecision(D59)));

            assertThatThrownBy(() -> CAPTAIN_ITEM.move(TaskStage.QUEUED, stillOpen)).isInstanceOf(Refusal.class)
                    .hasMessage("fit-the-lamp stays queued: D59 waits for its answer.");
        }

        @Test
        void refusesAShip() {
            assertThatThrownBy(() -> SHIP.move(TaskStage.QUEUED, new CaptainItemAnswered(Holds.none()))).isInstanceOf(Refusal.class)
                    .hasMessageContaining("only a captain item is closed by the answer to its decision");
        }
    }

    @Nested
    class Dropping {

        @ParameterizedTest
        @EnumSource(names = {"RETIRED", "DROPPED"}, mode = EnumSource.Mode.EXCLUDE)
        void dropsATaskWithNothingUnlanded(TaskStage stage) {
            StageChange change = SHIP.move(stage, new Drop(DROP_IT, List.of(), false));

            assertThat(change.to()).isEqualTo(TaskStage.DROPPED);
            assertThat(change.reason()).isEqualTo("dropped on the captain's word");
        }

        @Test
        void refusesWorkNotLandedUnlessTheWordDiscardsIt() {
            List<String> unlanded = List.of("commit b30e15a \"Apply the amendment\" is not in origin/main");

            assertThatThrownBy(() -> SHIP.move(TaskStage.CHANGING, new Drop(DROP_IT, unlanded, false))).isInstanceOf(Refusal.class)
                    .hasMessage("fit-the-lamp stays changing: commit b30e15a \"Apply the amendment\" is not in origin/main,"
                            + " and the captain's word does not discard it.");
            assertThat(SHIP.move(TaskStage.CHANGING, new Drop(DROP_IT, unlanded, true)).to()).isEqualTo(TaskStage.DROPPED);
        }

        @Test
        void refusesADropWithoutTheCaptainsWord() {
            assertThatThrownBy(() -> new Drop(null, List.of(), false)).isInstanceOf(NullPointerException.class)
                    .hasMessage("A task is dropped only on the captain's word.");
        }

        @ParameterizedTest
        @EnumSource(names = {"RETIRED", "DROPPED"})
        void refusesARetiredOrDroppedTask(TaskStage stage) {
            assertThatThrownBy(() -> SHIP.move(stage, new Drop(DROP_IT, List.of(), false))).isInstanceOf(Refusal.class)
                    .hasMessageEndingWith("a task becomes dropped only from queued, working, delivered, changing, ready, merged or landed.");
        }
    }

    @Nested
    class TheFourDefectsOfWeekOne {

        @Test
        void aSteerAfterADoneHoldsTheMergeOfTheOldHeadAsUnlandedWork() {
            StageChange steered = SHIP.move(TaskStage.DELIVERED, new Change(ChangeCause.STEER_SENT));
            DoneReported doneWithTheSteerUnread = new DoneReported(List.of(), List.of(), List.of(2), true, List.of(), false);

            assertThat(steered.to()).isEqualTo(TaskStage.CHANGING);
            assertThatThrownBy(() -> SHIP.move(steered.to(), doneWithTheSteerUnread)).isInstanceOf(Refusal.class)
                    .hasMessageContaining("message 2 waits unacknowledged");
            assertThat(SHIP.move(steered.to(), aCleanDone()).to()).isEqualTo(TaskStage.DELIVERED);

            StageChange mergedWhileChanging = SHIP.move(steered.to(), new RequestMerged(OLD_HEAD, List.of(THE_STEERS_COMMIT)));

            assertThat(mergedWhileChanging.to()).isEqualTo(TaskStage.MERGED);
            assertThat(mergedWhileChanging.reason()).isEqualTo("merged at 7e23dd8; 1 commit not landed: b30e15a");
            assertThat(mergedWhileChanging.raisesAFollowUpDecision()).isTrue();
        }

        @Test
        void anAnswerToAFinishedScoutsDecisionLeavesItDeliveredAndNotOngoing() {
            TaskStage delivered = SCOUT.move(TaskStage.WORKING, new DoneReported(List.of(), List.of(), List.of(), false, List.of(), true)).to();
            Holds waitingOnD38 = Holds.none().with(new OpenDecision(new DecisionNumber(38)));

            Holds afterTheAnswer = waitingOnD38.answered(new DecisionNumber(38));

            assertThat(delivered).isEqualTo(TaskStage.DELIVERED);
            assertThat(delivered.isOngoing()).isFalse();
            assertThat(afterTheAnswer.isEmpty()).isTrue();
        }

        @Test
        void aSquashMergeLandsByItsMergedHeadWhateverTheBaseChangedSince() {
            StageChange squashed = SHIP.move(TaskStage.READY, new RequestMerged(OLD_HEAD, List.of()));

            assertThat(SHIP.move(squashed.to(), new LandingObserved(true, List.of())).to()).isEqualTo(TaskStage.LANDED);
            assertThatThrownBy(() -> SHIP.move(squashed.to(), new LandingObserved(true, List.of(THE_STEERS_COMMIT))))
                    .isInstanceOf(Refusal.class).hasMessage("fit-the-lamp stays merged: 1 commit not landed: b30e15a.");
        }

        @Test
        void aRequestIsNotReadyWhileItsPipelineRuns() {
            ForgeObserved stillRunning = new ForgeObserved(true, false, true);

            assertThatThrownBy(() -> SHIP.move(TaskStage.DELIVERED, stillRunning)).isInstanceOf(Refusal.class)
                    .hasMessageContaining("its latest pipeline run has not passed");

            TaskStage ready = SHIP.move(TaskStage.DELIVERED, readyOnTheForge()).to();

            assertThat(ready).isEqualTo(TaskStage.READY);
            assertThat(SHIP.move(ready, new Change(ChangeCause.PIPELINE_RUN_STARTED)).to()).isEqualTo(TaskStage.CHANGING);
        }
    }

    private static Dispatch dispatchWithRoom() {
        return new Dispatch(Holds.none(), List.of(), true);
    }

    private static DoneReported aCleanDone() {
        return new DoneReported(List.of(), List.of(), List.of(), false, List.of(), false);
    }

    private static ForgeObserved readyOnTheForge() {
        return new ForgeObserved(false, true, true);
    }
}
