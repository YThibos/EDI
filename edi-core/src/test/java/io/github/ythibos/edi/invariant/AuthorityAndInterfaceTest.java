package io.github.ythibos.edi.invariant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("4.1 Authority and interface")
class AuthorityAndInterfaceTest {

    @Nested
    @DisplayName("I-01 One interface")
    class OneInterface {

        @Test
        @DisplayName("AT-01a the generated brief contains the rule and no worker-facing text addresses the captain")
        void theBriefCarriesTheRuleAndNeverAddressesTheCaptain() {
            Pending.until(Slice.S3);
        }

        @Test
        @DisplayName("AT-01b edi status rejects text that begins with the address word or 'Captain,' and tells the worker why")
        void statusRejectsTextAddressedToTheCaptain() {
            Pending.until(Slice.S3);
        }
    }

    @Nested
    @DisplayName("I-02 The captain's current session is primary")
    class TheCaptainsSessionIsPrimary {

        @Test
        @DisplayName("AT-02 a typed prompt in a second session moves the primary role and the first session's mutating calls are refused")
        void aTypedPromptMovesThePrimaryRole() {
            Pending.until(Slice.S4);
        }
    }

    @Nested
    @DisplayName("I-03 No merge capability")
    class NoMergeCapability {

        @Test
        @DisplayName("AT-03a no adapter method or HTTP call names merge, auto-merge or merge_when_pipeline_succeeds")
        void noAdapterMethodOrHttpCallNamesAMerge() {
            Pending.until(Slice.S5);
        }

        @Test
        @DisplayName("AT-03b the forge command allow-lists hold no merge command and no push to a base branch")
        void theForgeAllowListsHoldNoMerge() {
            Pending.until(Slice.S5);
        }
    }

    @Nested
    @DisplayName("I-04 Drafts only")
    class DraftsOnly {

        @Test
        @DisplayName("AT-04 delivery refuses a pipeline outcome whose merge request is not a draft and records a failure")
        void deliveryRefusesANonDraft() {
            Pending.until(Slice.S5);
        }
    }

    @Nested
    @DisplayName("I-05 A report authorises nothing")
    class AReportAuthorisesNothing {

        @Test
        @DisplayName("AT-05 edi task promote refuses a scout without a recorded captain authorisation")
        void promotionNeedsTheCaptainsAuthorisation() {
            Pending.until(Slice.S8);
        }
    }

    @Nested
    @DisplayName("I-06 Destructive, irreversible and security-sensitive actions need the captain's word in the current exchange")
    class DestructiveActionsNeedTheCaptainsWord {

        @Test
        @DisplayName("AT-06 discarding a worktree or work, deleting an unlanded branch and touching secrets need a decision answered this session")
        void destructiveCommandsNeedAnAnswerFromThisSession() {
            Pending.until(Slice.S8);
        }
    }
}
