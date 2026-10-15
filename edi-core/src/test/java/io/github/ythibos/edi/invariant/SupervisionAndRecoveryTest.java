package io.github.ythibos.edi.invariant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("4.5 Supervision and recovery")
class SupervisionAndRecoveryTest {

    @Nested
    @DisplayName("I-23 A restart is a non-event")
    class ARestartIsANonEvent {

        @Test
        @DisplayName("AT-23 after killing the core and the primary mid-task, the session-start digest equals the pre-crash snapshot")
        void theDigestSurvivesARestart() {
            Pending.until(Slice.S4);
        }
    }

    @Nested
    @DisplayName("I-24 An outage is a numbered list")
    class AnOutageIsANumberedList {

        @Test
        @DisplayName("AT-24 three dead workers give three holds in a stable numbered list and '2 resume' relaunches only task 2")
        void deadWorkersBecomeANumberedHoldList() {
            Pending.until(Slice.S4);
        }
    }

    @Nested
    @DisplayName("I-25 Relaunch reuses the task's own worktree and session")
    class RelaunchReusesWorktreeAndSession {

        @Test
        @DisplayName("AT-25 a relaunch after a lost server lands in the recorded path with the recorded session id")
        void aRelaunchResumesTheRecordedSession() {
            Pending.until(Slice.S4);
        }
    }

    @Nested
    @DisplayName("I-26 Judge workers by structured state")
    class JudgeWorkersByStructuredState {

        @Test
        @DisplayName("AT-26 a worker whose last status is working but whose tmux server identity changed is reported dead")
        void aChangedServerIdentityMeansDead() {
            Pending.until(Slice.S4);
        }
    }

    @Nested
    @DisplayName("I-27 A steer is verified as landed")
    class ASteerIsVerified {

        @Test
        @DisplayName("AT-27 an unacknowledged message raises a 'steer not received' event after the timeout")
        void anUnacknowledgedSteerRaisesAnEvent() {
            Pending.until(Slice.S3);
        }
    }

    @Nested
    @DisplayName("I-28 Never press a key into a dialog blind")
    class NeverPressAKeyBlind {

        @Test
        @DisplayName("AT-28 the tmux adapter sends only literal text plus Enter after a positive idle probe, or Escape")
        void theTmuxAdapterRefusesOtherKeys() {
            Pending.until(Slice.S3);
        }
    }

    @Nested
    @DisplayName("I-29 A captain-side merge refreshes the clone before follow-up work")
    class AMergeRefreshesTheClone {

        @Test
        @DisplayName("AT-29 follow-up work on a project with a pending refresh waits until the fetch verified the merge commit")
        void followUpWaitsForTheVerifiedRefresh() {
            Pending.until(Slice.S5);
        }
    }
}
