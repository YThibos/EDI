package io.github.ythibos.edi.domain.primary;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.UndeliveredAnswer;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.wake.WakeStream;

class PrimaryRulesTest {

    private static final Instant START = Instant.parse("2026-10-16T09:00:00Z");

    @Test
    void tellsTheCaptainsTypedPromptsFromNoticesAndInjectedLines() {
        assertThat(new Prompt("Fit the brass lamp.").isTypedByTheCaptain()).isTrue();
        assertThat(new Prompt("<task-notification>\n<status>completed</status>\n</task-notification>").isTypedByTheCaptain()).isFalse();
        assertThat(new Prompt("⁣EDI_OP: message 1 waiting: run edi inbox").isTypedByTheCaptain()).isFalse();
        assertThat(new Prompt("EDI_OP: message 1 waiting: run edi inbox").isTypedByTheCaptain()).isFalse();
        assertThat(new Prompt("   ").isTypedByTheCaptain()).isFalse();
        assertThat(new Prompt(null).isTypedByTheCaptain()).isFalse();
    }

    @Test
    void givesTheRoleToTheLiveSessionThatClaimedItLast() {
        PrimarySession earlier = PrimarySession.started(UUID.randomUUID(), null, START).claimed(START);
        PrimarySession later = PrimarySession.started(UUID.randomUUID(), new SessionProcess(41, 9000L), START).claimed(START.plusSeconds(5));
        PrimarySession unclaimed = PrimarySession.started(UUID.randomUUID(), null, START);

        assertThat(PrimaryRole.among(List.of(earlier, later, unclaimed), _ -> true).standingOf(later.id())).isEqualTo(Standing.PRIMARY);
        assertThat(PrimaryRole.among(List.of(earlier, later, unclaimed), _ -> true).standingOf(earlier.id())).isEqualTo(Standing.STANDBY);
        assertThat(PrimaryRole.among(List.of(earlier, later), _ -> false).standingOf(earlier.id())).isEqualTo(Standing.PRIMARY);
        assertThat(PrimaryRole.among(List.of(earlier.ended(START), later.ended(START)), _ -> true).isVacant()).isTrue();
        assertThat(PrimaryRole.among(List.of(unclaimed), _ -> true).standingOf(unclaimed.id())).isEqualTo(Standing.UNCLAIMED);
    }

    @Test
    void tellsTheRecordedProcessFromOneThatReusedItsIdByItsStartTick() {
        SessionProcess recorded = new SessionProcess(41, 9000L);

        assertThat(recorded.isTheOneStartedAt(9000)).isTrue();
        assertThat(recorded.isTheOneStartedAt(9001)).isFalse();
        assertThat(recorded.isTheOneStartedAt(8999)).isFalse();
        assertThat(new SessionProcess(41, null).isTheOneStartedAt(9000)).isTrue();
    }

    @Test
    void opensTheSitrepOnlyForTheLaunchOfANewSessionThatHoldsThePrimaryRole() {
        assertThat(new SessionStart(true, true, Standing.PRIMARY).opensTheSitrep()).isTrue();
        assertThat(new SessionStart(false, true, Standing.PRIMARY).opensTheSitrep()).isFalse();
        assertThat(new SessionStart(true, false, Standing.PRIMARY).opensTheSitrep()).isFalse();
        assertThat(new SessionStart(true, true, Standing.STANDBY).opensTheSitrep()).isFalse();
        assertThat(new SessionStart(true, true, Standing.UNCLAIMED).opensTheSitrep()).isFalse();
    }

    @Test
    void endsATurnOnlyAfterTheReportWhenStateChangedAndWithAWaitWhileWorkIsInFlight() {
        PrimaryTurn turn = PrimaryTurn.startedAt(START, 12);

        assertThat(TurnEnd.blockingReason(turn, new TurnCircumstances(false, false, false, false, List.of()))).isEmpty();
        assertThat(TurnEnd.blockingReason(turn, new TurnCircumstances(true, false, false, false, List.of()))).contains(TurnEnd.RENDER_THE_REPORT);
        assertThat(TurnEnd.blockingReason(turn.reported(START.plusSeconds(1)), new TurnCircumstances(true, false, false, false, List.of()))).isEmpty();
        assertThat(TurnEnd.blockingReason(turn.reported(START.minusSeconds(1)), new TurnCircumstances(true, false, false, false, List.of())))
                .contains(TurnEnd.RENDER_THE_REPORT);
        assertThat(TurnEnd.blockingReason(turn, new TurnCircumstances(true, true, false, false, List.of()))).isEmpty();
        assertThat(TurnEnd.blockingReason(turn, new TurnCircumstances(true, true, true, false, List.of()))).contains(TurnEnd.START_THE_WAIT);
        assertThat(TurnEnd.blockingReason(turn, new TurnCircumstances(false, false, true, false, List.of()))).contains(TurnEnd.START_THE_WAIT);
        assertThat(TurnEnd.blockingReason(turn, new TurnCircumstances(false, false, true, true, List.of()))).isEmpty();
        assertThat(TurnEnd.blockingReason(turn.stopBlocked().stopBlocked().stopBlocked(), new TurnCircumstances(true, false, true, false, List.of())))
                .isEmpty();
        assertThat(TurnEnd.blockingReason(null, new TurnCircumstances(true, false, true, false, List.of()))).isEmpty();
    }

    @Test
    void holdsATurnFirstForAnAnsweredDecisionWhoseWorkerStillWaitsOnItsQuestion() {
        PrimaryTurn turn = PrimaryTurn.startedAt(START, 12);
        List<UndeliveredAnswer> undelivered = List.of(new UndeliveredAnswer(new DecisionNumber(7), new TaskId("fit-lamp"), new StatusKey("lamp-choice")),
                new UndeliveredAnswer(new DecisionNumber(9), new TaskId("hang-bell"), new StatusKey("nm-4-review")));

        assertThat(TurnEnd.blockingReason(turn, new TurnCircumstances(true, false, true, false, undelivered))).contains(
                "D7 is answered and the worker of fit-lamp still waits on its question lamp-choice: carry the answer with edi steer fit-lamp --resolves lamp-choice."
                        + " D9 is answered and the worker of hang-bell still waits on its question nm-4-review: carry the answer with edi steer hang-bell"
                        + " --resolves nm-4-review. Then stop.");
        assertThat(TurnEnd.blockingReason(turn.stopBlocked().stopBlocked().stopBlocked(), new TurnCircumstances(false, false, false, false, undelivered)))
                .isEmpty();
    }

    @Test
    void remembersTheLastReportOfAnEarlierTurnAcrossTurnsWithoutOne() {
        PrimarySession firstTurn = PrimarySession.started(UUID.randomUUID(), null, START).promptSubmitted(new Prompt("Fit the lamp."), 1, START);
        PrimarySession reportedTwice = firstTurn.reported(START.plusSeconds(5)).reported(START.plusSeconds(8));
        PrimarySession secondTurn = reportedTwice.promptSubmitted(new Prompt("And the bell?"), 2, START.plusSeconds(60));
        PrimarySession thirdTurn = secondTurn.promptSubmitted(new Prompt("<task-notification>"), 3, START.plusSeconds(120));
        PrimarySession reportedInTheThird = thirdTurn.reported(START.plusSeconds(125));

        assertThat(firstTurn.turn().previousReportAt()).isNull();
        assertThat(reportedTwice.turn().previousReportAt()).isNull();
        assertThat(secondTurn.turn().previousReportAt()).isEqualTo(START.plusSeconds(8));
        assertThat(thirdTurn.turn().previousReportAt()).isEqualTo(START.plusSeconds(8));
        assertThat(reportedInTheThird.turn().previousReportAt()).isEqualTo(START.plusSeconds(8));
        assertThat(reportedInTheThird.promptSubmitted(new Prompt("Next."), 4, START.plusSeconds(180)).turn().previousReportAt())
                .isEqualTo(START.plusSeconds(125));
        assertThat(secondTurn.turn().renderedTheReport()).isFalse();
    }

    @Test
    void tellsWhetherTheWaitIsLiveFromThePrimarysLiveWakeStream() {
        PrimarySession claimed = PrimarySession.started(UUID.randomUUID(), null, START).claimed(START);
        WakeStream following = new WakeStream(UUID.randomUUID(), claimed.id(), new SessionProcess(4242, 9L), true, START.plusSeconds(20));
        WakeStream once = new WakeStream(UUID.randomUUID(), claimed.id(), new SessionProcess(4242, 9L), false, START.plusSeconds(30));

        assertThat(PrimaryWait.of(Optional.of(claimed), Optional.of(following), 2))
                .isEqualTo(new PrimaryWait(PrimaryWait.Sighting.FOLLOWING, START.plusSeconds(20), 2));
        assertThat(PrimaryWait.of(Optional.of(claimed), Optional.of(once), 2))
                .isEqualTo(new PrimaryWait(PrimaryWait.Sighting.WAITING_ONCE, START.plusSeconds(30), 2));
        assertThat(PrimaryWait.of(Optional.of(claimed), Optional.empty(), 2)).isEqualTo(new PrimaryWait(PrimaryWait.Sighting.MISSING, null, 2));
        assertThat(PrimaryWait.of(Optional.empty(), Optional.empty(), 2).sighting()).isEqualTo(PrimaryWait.Sighting.NO_PRIMARY);
    }

    @Test
    void needsAWaitOnlyWhileWorkIsInFlightAndNoWakeStreamIsLive() {
        PrimarySession claimed = PrimarySession.started(UUID.randomUUID(), null, START).claimed(START);
        WakeStream live = new WakeStream(UUID.randomUUID(), claimed.id(), new SessionProcess(4242, 9L), true, START);

        assertThat(PrimaryWait.of(Optional.of(claimed), Optional.empty(), 1).needsAWait()).isTrue();
        assertThat(PrimaryWait.of(Optional.empty(), Optional.empty(), 1).needsAWait()).isTrue();
        assertThat(PrimaryWait.of(Optional.of(claimed), Optional.empty(), 0).needsAWait()).isFalse();
        assertThat(PrimaryWait.of(Optional.of(claimed), Optional.of(live), 1).needsAWait()).isFalse();
    }
}
