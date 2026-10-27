package io.github.ythibos.edi.domain.task;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.ythibos.edi.domain.landing.BranchCommit;

public sealed interface StageTrigger {

    record Dispatch(Holds holds, List<TaskId> unmetWaits, boolean capacityFree) implements StageTrigger {

        public Dispatch {
            unmetWaits = List.copyOf(unmetWaits);
        }
    }

    record DoneReported(
            List<String> uncommittedPaths,
            List<String> snapshotVersions,
            List<Integer> unacknowledgedMessages,
            boolean pipelineRunning,
            List<String> requestGaps,
            boolean reportFiled) implements StageTrigger {

        public DoneReported {
            uncommittedPaths = List.copyOf(uncommittedPaths);
            snapshotVersions = List.copyOf(snapshotVersions);
            unacknowledgedMessages = List.copyOf(unacknowledgedMessages);
            requestGaps = List.copyOf(requestGaps);
        }
    }

    record ForgeObserved(boolean draft, boolean latestRunPassed, boolean atTheDeliveredHead) implements StageTrigger {
    }

    record Change(ChangeCause cause) implements StageTrigger {

        public Change {
            Objects.requireNonNull(cause, "A change says what began it.");
        }
    }

    record RequestMerged(String mergedHead, List<BranchCommit> commitsAfterTheMergedHead) implements StageTrigger {

        public RequestMerged {
            if (mergedHead == null || mergedHead.isBlank()) {
                throw new IllegalArgumentException("A followed merge records the head the forge merged.");
            }
            commitsAfterTheMergedHead = List.copyOf(commitsAfterTheMergedHead);
        }
    }

    record LandingObserved(boolean landedCommitInTheBase, List<BranchCommit> commitsAfterTheMergedHead) implements StageTrigger {

        public LandingObserved {
            commitsAfterTheMergedHead = List.copyOf(commitsAfterTheMergedHead);
        }
    }

    record CleanUp(Set<Leftover> leftovers, boolean reportFiled) implements StageTrigger {

        public CleanUp {
            leftovers = Set.copyOf(leftovers);
        }
    }

    record CaptainItemAnswered(Holds holdsAfterTheAnswer) implements StageTrigger {
    }

    record Drop(CaptainsWord word, List<String> unlandedWork, boolean discardingWork) implements StageTrigger {

        public Drop {
            Objects.requireNonNull(word, "A task is dropped only on the captain's word.");
            unlandedWork = List.copyOf(unlandedWork);
        }
    }
}
