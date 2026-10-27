package io.github.ythibos.edi.domain.primary;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

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

        assertThat(TurnEnd.blockingReason(turn, new TurnCircumstances(false, false, false, false))).isEmpty();
        assertThat(TurnEnd.blockingReason(turn, new TurnCircumstances(true, false, false, false))).contains(TurnEnd.RENDER_THE_REPORT);
        assertThat(TurnEnd.blockingReason(turn.reported(START.plusSeconds(1)), new TurnCircumstances(true, false, false, false))).isEmpty();
        assertThat(TurnEnd.blockingReason(turn.reported(START.minusSeconds(1)), new TurnCircumstances(true, false, false, false)))
                .contains(TurnEnd.RENDER_THE_REPORT);
        assertThat(TurnEnd.blockingReason(turn, new TurnCircumstances(true, true, false, false))).isEmpty();
        assertThat(TurnEnd.blockingReason(turn, new TurnCircumstances(true, true, true, false))).contains(TurnEnd.START_THE_WAIT);
        assertThat(TurnEnd.blockingReason(turn, new TurnCircumstances(false, false, true, false))).contains(TurnEnd.START_THE_WAIT);
        assertThat(TurnEnd.blockingReason(turn, new TurnCircumstances(false, false, true, true))).isEmpty();
        assertThat(TurnEnd.blockingReason(turn.stopBlocked().stopBlocked().stopBlocked(), new TurnCircumstances(true, false, true, false))).isEmpty();
        assertThat(TurnEnd.blockingReason(null, new TurnCircumstances(true, false, true, false))).isEmpty();
    }

    @Test
    void recognisesALiveEdiWaitAmongTheSessionsBackgroundJobs() {
        assertThat(BackgroundJob.anyWaitsForEdi(List.of(new BackgroundJob("shell", "running", "edi wait")))).isTrue();
        assertThat(BackgroundJob.anyWaitsForEdi(List.of(new BackgroundJob("shell", "pending", "cd ~/.edi && /usr/local/bin/edi wait 2>&1")))).isTrue();
        assertThat(BackgroundJob.anyWaitsForEdi(List.of(new BackgroundJob("shell", "completed", "edi wait")))).isFalse();
        assertThat(BackgroundJob.anyWaitsForEdi(List.of(new BackgroundJob("shell", "running", "edi worker list")))).isFalse();
        assertThat(BackgroundJob.anyWaitsForEdi(List.of(new BackgroundJob("shell", "running", "echo edi wait")))).isFalse();
        assertThat(BackgroundJob.anyWaitsForEdi(List.of(new BackgroundJob("subagent", "running", "edi wait")))).isFalse();
        assertThat(BackgroundJob.anyWaitsForEdi(List.of(new BackgroundJob("shell", "running", "edi wait | tee wake.txt")))).isTrue();
        assertThat(BackgroundJob.anyWaitsForEdi(List.of())).isFalse();
    }

    @Test
    void tellsWhetherTheWaitIsLiveFromWhatThePrimarysLastStopSaw() {
        PrimarySession claimed = PrimarySession.started(UUID.randomUUID(), null, START).claimed(START);
        PrimarySession inATurn = claimed.promptSubmitted(new Prompt("How is the lamp?"), 7, START.plusSeconds(10));
        PrimarySession endedWaiting = inATurn.turnEnded(START.plusSeconds(20), true);
        PrimarySession endedBlind = inATurn.turnEnded(START.plusSeconds(20), false);

        assertThat(PrimaryWait.of(Optional.of(endedWaiting), 2)).isEqualTo(new PrimaryWait(PrimaryWait.Sighting.LIVE, START.plusSeconds(20), 2));
        assertThat(PrimaryWait.of(Optional.of(endedBlind), 2)).isEqualTo(new PrimaryWait(PrimaryWait.Sighting.MISSING, START.plusSeconds(20), 2));
        assertThat(PrimaryWait.of(Optional.of(inATurn), 2).sighting()).isEqualTo(PrimaryWait.Sighting.UNCHECKED_IN_A_TURN);
        assertThat(PrimaryWait.of(Optional.of(inATurn), 2).seenAt()).isEqualTo(START.plusSeconds(10));
        assertThat(PrimaryWait.of(Optional.of(claimed), 2)).isEqualTo(new PrimaryWait(PrimaryWait.Sighting.MISSING_SINCE_SESSION_START, START, 2));
        assertThat(PrimaryWait.of(Optional.of(endedWaiting.restarted(null, SessionSource.RESTART, START.plusSeconds(40))), 2))
                .isEqualTo(new PrimaryWait(PrimaryWait.Sighting.MISSING, START.plusSeconds(40), 2));
        assertThat(PrimaryWait.of(Optional.of(endedWaiting.restarted(null, SessionSource.COMPACTION, START.plusSeconds(40))), 2))
                .isEqualTo(new PrimaryWait(PrimaryWait.Sighting.LIVE, START.plusSeconds(20), 2));
        assertThat(PrimaryWait.of(Optional.of(inATurn.turnFailed(START.plusSeconds(40))), 2))
                .isEqualTo(new PrimaryWait(PrimaryWait.Sighting.MISSING, START.plusSeconds(40), 2));
        assertThat(PrimaryWait.of(Optional.empty(), 2).sighting()).isEqualTo(PrimaryWait.Sighting.NO_PRIMARY);
        assertThat(endedBlind.promptSubmitted(new Prompt("And now?"), 8, START.plusSeconds(30)).turn().hasEnded()).isFalse();
    }

    @Test
    void needsAWaitOnlyWhileWorkIsInFlightAndTheLastStopSawNone() {
        PrimarySession inATurn = PrimarySession.started(UUID.randomUUID(), null, START).claimed(START)
                .promptSubmitted(new Prompt("How is the lamp?"), 7, START.plusSeconds(10));

        assertThat(PrimaryWait.of(Optional.of(inATurn.turnEnded(START.plusSeconds(20), false)), 1).needsAWait()).isTrue();
        assertThat(PrimaryWait.of(Optional.empty(), 1).needsAWait()).isTrue();
        assertThat(PrimaryWait.of(Optional.of(inATurn.turnEnded(START.plusSeconds(20), false)), 0).needsAWait()).isFalse();
        assertThat(PrimaryWait.of(Optional.of(inATurn.turnEnded(START.plusSeconds(20), true)), 1).needsAWait()).isFalse();
        assertThat(PrimaryWait.of(Optional.of(inATurn), 1).needsAWait()).isFalse();
    }
}
