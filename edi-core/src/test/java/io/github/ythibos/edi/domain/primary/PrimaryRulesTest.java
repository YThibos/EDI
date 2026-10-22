package io.github.ythibos.edi.domain.primary;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
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
        PrimarySession later = PrimarySession.started(UUID.randomUUID(), new SessionProcess(41, START), START).claimed(START.plusSeconds(5));
        PrimarySession unclaimed = PrimarySession.started(UUID.randomUUID(), null, START);

        assertThat(PrimaryRole.among(List.of(earlier, later, unclaimed), _ -> true).standingOf(later.id())).isEqualTo(Standing.PRIMARY);
        assertThat(PrimaryRole.among(List.of(earlier, later, unclaimed), _ -> true).standingOf(earlier.id())).isEqualTo(Standing.STANDBY);
        assertThat(PrimaryRole.among(List.of(earlier, later), _ -> false).standingOf(earlier.id())).isEqualTo(Standing.PRIMARY);
        assertThat(PrimaryRole.among(List.of(earlier.ended(START), later.ended(START)), _ -> true).isVacant()).isTrue();
        assertThat(PrimaryRole.among(List.of(unclaimed), _ -> true).standingOf(unclaimed.id())).isEqualTo(Standing.UNCLAIMED);
    }

    @Test
    void tellsTheSameProcessAcrossBootTimeDriftFromOneThatReusedItsId() {
        SessionProcess recorded = new SessionProcess(41, START);

        assertThat(recorded.isTheOneStartedAt(START)).isTrue();
        assertThat(recorded.isTheOneStartedAt(START.plusSeconds(2))).isTrue();
        assertThat(recorded.isTheOneStartedAt(START.minusSeconds(2))).isTrue();
        assertThat(recorded.isTheOneStartedAt(START.plusSeconds(3))).isFalse();
        assertThat(recorded.isTheOneStartedAt(START.minusSeconds(3))).isFalse();
        assertThat(recorded.isTheOneStartedAt(START.plusSeconds(60))).isFalse();
        assertThat(new SessionProcess(41, null).isTheOneStartedAt(START)).isTrue();
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
}
