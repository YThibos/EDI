package io.github.ythibos.edi.invariant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("4.6 Process safety")
class ProcessSafetyTest {

    @Nested
    @DisplayName("I-30 No broad process signals")
    class NoBroadProcessSignals {

        @Test
        @DisplayName("AT-30 the pre-tool guard refuses pkill, killall and kill $(pgrep ...) in worker and primary Bash calls")
        void theGuardRefusesPatternKills() {
            Pending.until(Slice.S3);
        }
    }

    @Nested
    @DisplayName("I-31 Workers live on EDI's private tmux socket, and nobody runs bare-socket tmux")
    class WorkersLiveOnThePrivateSocket {

        @Test
        @DisplayName("AT-31 the guard refuses tmux kill-server, tmux kill-session without a socket, and tmux -L edi kill-server from a worker")
        void theGuardRefusesBareSocketTmux() {
            Pending.until(Slice.S3);
        }
    }

    @Nested
    @DisplayName("I-32 Secrets are read at call time and never printed")
    class SecretsAreNeverPrinted {

        @Test
        @DisplayName("AT-32 a canary secret in a fixture keys file never appears in logs, events or stdout after a full adapter run")
        void aCanarySecretNeverSurfaces() {
            Pending.until(Slice.S6);
        }
    }
}
