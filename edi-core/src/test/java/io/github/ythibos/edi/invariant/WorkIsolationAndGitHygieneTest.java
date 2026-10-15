package io.github.ythibos.edi.invariant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("4.2 Work isolation and git hygiene")
class WorkIsolationAndGitHygieneTest {

    @Nested
    @DisplayName("I-07 Workers never touch the primary copy or the captain's checkouts")
    class WorkersStayInTheirWorktree {

        @Test
        @DisplayName("AT-07a a worker launch refuses a worktree outside $EDI_HOME/worktrees/ or in the project clone's primary directory")
        void launchRefusesAWorktreeOutsideTheHome() {
            Pending.until(Slice.S3);
        }

        @Test
        @DisplayName("AT-07b delivery into a captain checkout is a ref-only fetch that leaves a dirty checkout byte-identical")
        void deliveryLeavesTheCaptainsCheckoutUntouched() {
            Pending.until(Slice.S3);
        }
    }

    @Nested
    @DisplayName("I-08 Branch names carry the work key, never fm/ or edi/")
    class BranchNamesCarryTheWorkKey {

        @Test
        @DisplayName("AT-08 branch naming refuses a missing key and any fm/ or edi/ prefix for every project, including EDI itself")
        void branchNamingRefusesAMissingKeyAndForbiddenPrefixes() {
            Pending.until(Slice.S3);
        }
    }

    @Nested
    @DisplayName("I-09 No co-author or agent trailer, ever")
    class NoAgentTrailer {

        @Test
        @DisplayName("AT-09 the commit-msg hook rejects each forbidden trailer form and launch settings carry the attribution block")
        void theCommitHookRejectsAgentTrailers() {
            Pending.until(Slice.S3);
        }
    }

    @Nested
    @DisplayName("I-10 Every commit carries its key")
    class EveryCommitCarriesItsKey {

        @Test
        @DisplayName("AT-10 the commit-msg hook rejects a subject without the task's key")
        void theCommitHookRejectsASubjectWithoutTheKey() {
            Pending.until(Slice.S3);
        }
    }

    @Nested
    @DisplayName("I-11 No SNAPSHOT in push-bound work")
    class NoSnapshotInPushBoundWork {

        @Test
        @DisplayName("AT-11 a branch adding a SNAPSHOT dependency makes edi status done refuse with the file and line")
        void doneRefusesASnapshotDependency() {
            Pending.until(Slice.S5);
        }
    }

    @Nested
    @DisplayName("I-12 Never discard unlanded work")
    class NeverDiscardUnlandedWork {

        @Test
        @DisplayName("AT-12 the landed-work test tells uncommitted, unpushed and unmerged work from squash-merged or rebuilt work")
        void theLandedWorkTestRecognisesLandedAndUnlandedWork() {
            Pending.until(Slice.S5);
        }
    }

    @Nested
    @DisplayName("I-13 Base branches come from the origin ref")
    class BaseBranchesComeFromOrigin {

        @Test
        @DisplayName("AT-13 worktree creation fetches and starts at the origin ref so a stale local ref does not leak in")
        void worktreesStartAtTheFreshOriginRef() {
            Pending.until(Slice.S5);
        }
    }
}
