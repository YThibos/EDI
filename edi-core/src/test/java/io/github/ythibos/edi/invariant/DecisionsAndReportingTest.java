package io.github.ythibos.edi.invariant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("4.4 Decisions and reporting")
class DecisionsAndReportingTest {

    @Nested
    @DisplayName("I-18 Decisions are durable and numbered")
    class DecisionsAreDurableAndNumbered {

        @Test
        @DisplayName("AT-18 assigning, re-holding, closing, archiving and assigning again yields strictly increasing D numbers from the migrated sequence")
        void decisionNumbersStrictlyIncrease() {
            Pending.until(Slice.S1);
        }
    }

    @Nested
    @DisplayName("I-19 Every open decision appears in every captain report until answered")
    class OpenDecisionsAlwaysAppear {

        @Test
        @DisplayName("AT-19 a decision absent from the turn input still renders in the report")
        void anOpenDecisionRendersWithoutTurnInput() {
            Pending.until(Slice.S1);
        }
    }

    @Nested
    @DisplayName("I-20 The captain's answer is recorded verbatim with its interpretation")
    class AnswersAreRecordedVerbatim {

        @Test
        @DisplayName("AT-20 answering stores the exact text, the option key and the interpretation, and the decision leaves the report")
        void answeringStoresTheVerbatimAnswer() {
            Pending.until(Slice.S1);
        }
    }

    @Nested
    @DisplayName("I-21 Every state-changing turn ends with the boxed captain report")
    class StateChangingTurnsEndWithTheReport {

        @Test
        @DisplayName("AT-21 the primary's Stop hook blocks a state-changing turn until edi report ran in that turn")
        void theStopHookWaitsForTheReport() {
            Pending.until(Slice.S4);
        }
    }

    @Nested
    @DisplayName("I-22 Talk in outcomes")
    class TalkInOutcomes {

        @Test
        @DisplayName("AT-22 a lint over captain-facing strings rejects the internal-label list")
        void captainFacingStringsCarryNoInternalLabels() {
            Pending.until(Slice.S8);
        }
    }
}
