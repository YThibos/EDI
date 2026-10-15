package io.github.ythibos.edi.invariant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Acceptance tests beyond the invariants (12.7 and 17.4)")
class BeyondTheInvariantsTest {

    @Test
    @DisplayName("AT-39 the native CLI round trip has a p95 under 50 ms over 200 calls on the laptop")
    void theNativeCommandAnswersQuickly() {
        Pending.until(Slice.S1);
    }

    @Test
    @DisplayName("AT-40 a deliberately leaked child process in a test fails the build")
    void aLeakedChildProcessFailsTheBuild() {
        Pending.until(Slice.S3);
    }

    @Test
    @DisplayName("AT-41 the migration dry run on a copy of the old home reproduces its counts and reports every unmapped item")
    void theMigrationDryRunReproducesTheCounts() {
        Pending.until(Slice.S1);
    }

    @Test
    @DisplayName("AT-42 the report presenter matches the old renderer on every ported golden case and the 2 MB backlog case")
    void theReportMatchesTheGoldenCases() {
        Pending.until(Slice.S2);
    }

    @Test
    @DisplayName("AT-46 presence switches to quiet without input, stays silent on routine wakes, never turns silence into away, and demo holds present")
    void presenceFollowsTheCaptain() {
        Pending.until(Slice.S8);
    }
}
