package io.github.ythibos.edi.invariant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("4.8 Two machines")
class TwoMachinesTest {

    @Nested
    @DisplayName("I-37 One codebase, per-machine choices")
    class OneCodebasePerMachineChoices {

        @Test
        @DisplayName("AT-37 the full test suite runs green under both the laptop and desktop fixture profiles")
        void theSuitePassesUnderBothProfiles() {
            Pending.until(Slice.S6);
        }
    }

    @Nested
    @DisplayName("I-38 No secret and no personal material is ever published")
    class NothingPersonalIsPublished {

        @Test
        @DisplayName("AT-38 fixture branches with a secret, a deny-listed term or a personal path are refused at edi deliver and at the worker's push")
        void deliveryAndPushRefuseEachKindOfLeak() {
            Pending.until(Slice.S5);
        }
    }

    @Nested
    @DisplayName("I-39 Publication targets are declared, never inferred")
    class PublicationTargetsAreDeclared {

        @Test
        @DisplayName("AT-43 a worktree whose origin points at another repository is refused at push and at edi deliver")
        void aChangedOriginIsRefused() {
            Pending.until(Slice.S5);
        }
    }
}
