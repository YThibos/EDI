package io.github.ythibos.edi.domain.task;

import static io.github.ythibos.edi.domain.task.TaskStage.CHANGING;
import static io.github.ythibos.edi.domain.task.TaskStage.DELIVERED;
import static io.github.ythibos.edi.domain.task.TaskStage.DROPPED;
import static io.github.ythibos.edi.domain.task.TaskStage.LANDED;
import static io.github.ythibos.edi.domain.task.TaskStage.MERGED;
import static io.github.ythibos.edi.domain.task.TaskStage.QUEUED;
import static io.github.ythibos.edi.domain.task.TaskStage.READY;
import static io.github.ythibos.edi.domain.task.TaskStage.RETIRED;
import static io.github.ythibos.edi.domain.task.TaskStage.WORKING;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.landing.BranchCommit;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.task.StageTrigger.CaptainItemAnswered;
import io.github.ythibos.edi.domain.task.StageTrigger.Change;
import io.github.ythibos.edi.domain.task.StageTrigger.CleanUp;
import io.github.ythibos.edi.domain.task.StageTrigger.Dispatch;
import io.github.ythibos.edi.domain.task.StageTrigger.DoneReported;
import io.github.ythibos.edi.domain.task.StageTrigger.Drop;
import io.github.ythibos.edi.domain.task.StageTrigger.ForgeObserved;
import io.github.ythibos.edi.domain.task.StageTrigger.LandingObserved;
import io.github.ythibos.edi.domain.task.StageTrigger.RequestMerged;

public record StageTransitions(TaskId task, TaskKind kind) {

    private static final Set<TaskStage> MERGEABLE = EnumSet.of(WORKING, DELIVERED, CHANGING, READY);
    private static final Set<TaskStage> DROPPABLE = EnumSet.complementOf(EnumSet.of(RETIRED, DROPPED));
    private static final String REPORT_NOT_FILED = "its report.md is not filed";

    public StageChange move(TaskStage stage, StageTrigger trigger) {
        return switch (trigger) {
            case Dispatch dispatch -> dispatched(stage, dispatch);
            case DoneReported done -> delivered(stage, done);
            case ForgeObserved view -> readied(stage, view);
            case Change change -> changing(stage, change);
            case RequestMerged merge -> merged(stage, merge);
            case LandingObserved landing -> landed(stage, landing);
            case CleanUp cleanUp -> retired(stage, cleanUp);
            case CaptainItemAnswered answered -> closedByItsAnswer(stage, answered);
            case Drop drop -> dropped(stage, drop);
        };
    }

    private StageChange dispatched(TaskStage stage, Dispatch dispatch) {
        requireFrom(stage, EnumSet.of(QUEUED), WORKING);
        refuseAnyOf(stage, dispatchGapsOf(dispatch));
        return change(stage, WORKING, "dispatched");
    }

    private void requireFrom(TaskStage stage, Set<TaskStage> from, TaskStage to) {
        if (!from.contains(stage)) {
            throw new Refusal(Rule.TASK_STAGE, task + " is " + stage.word() + ", and a task becomes " + to.word() + " only from " + wordsOf(from) + ".");
        }
    }

    private static String wordsOf(Set<TaskStage> stages) {
        return listed(stages.stream().map(TaskStage::word).toList(), "or");
    }

    private static String listed(List<String> items, String conjunction) {
        if (items.size() == 1) {
            return items.getFirst();
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " " + conjunction + " " + items.getLast();
    }

    private void refuseAnyOf(TaskStage stage, List<String> gaps) {
        if (!gaps.isEmpty()) {
            throw new Refusal(Rule.TASK_STAGE, task + " stays " + stage.word() + ": " + String.join("; ", gaps) + ".");
        }
    }

    private List<String> dispatchGapsOf(Dispatch dispatch) {
        List<String> gaps = new ArrayList<>();
        if (kind == TaskKind.CAPTAIN) {
            gaps.add("a captain item gets no worker");
        }
        gaps.addAll(dispatch.holds().reasons());
        if (!dispatch.unmetWaits().isEmpty()) {
            gaps.add("it waits for " + listed(dispatch.unmetWaits().stream().map(TaskId::toString).toList(), "and"));
        }
        if (!dispatch.capacityFree()) {
            gaps.add("its project or plan has no room for another worker");
        }
        return gaps;
    }

    private StageChange change(TaskStage from, TaskStage to, String reason) {
        return new StageChange(task, from, to, reason, List.of());
    }

    private StageChange delivered(TaskStage stage, DoneReported done) {
        requireFrom(stage, EnumSet.of(WORKING, CHANGING), DELIVERED);
        refuseAnyOf(stage, deliveryGapsOf(done));
        return change(stage, DELIVERED, "its worker reported done");
    }

    private List<String> deliveryGapsOf(DoneReported done) {
        List<String> gaps = new ArrayList<>();
        done.uncommittedPaths().forEach(path -> gaps.add(path + " is not committed"));
        done.snapshotVersions().forEach(file -> gaps.add(file + " adds a SNAPSHOT version"));
        if (!done.unacknowledgedMessages().isEmpty()) {
            gaps.add(unacknowledgedGapOf(done.unacknowledgedMessages()));
        }
        if (done.pipelineRunning()) {
            gaps.add("its pipeline run is still running");
        }
        gaps.addAll(handOverGapsOf(done));
        return gaps;
    }

    private static String unacknowledgedGapOf(List<Integer> messages) {
        String numbers = listed(messages.stream().map(String::valueOf).toList(), "and");
        return messages.size() == 1 ? "message " + numbers + " waits unacknowledged" : "messages " + numbers + " wait unacknowledged";
    }

    private List<String> handOverGapsOf(DoneReported done) {
        return switch (kind) {
            case SHIP -> done.requestGaps();
            case SCOUT -> done.reportFiled() ? List.of() : List.of(REPORT_NOT_FILED);
            case CAPTAIN -> List.of();
        };
    }

    private StageChange readied(TaskStage stage, ForgeObserved view) {
        requireFrom(stage, EnumSet.of(DELIVERED), READY);
        refuseAnyOf(stage, readinessGapsOf(view));
        return change(stage, READY, "its request is ready for review");
    }

    private List<String> readinessGapsOf(ForgeObserved view) {
        List<String> gaps = new ArrayList<>();
        if (kind != TaskKind.SHIP) {
            gaps.add("only a ship's request is reviewed on the forge");
        }
        if (view.draft()) {
            gaps.add("its request is a draft");
        }
        if (!view.latestRunPassed()) {
            gaps.add("its latest pipeline run has not passed");
        }
        if (!view.atTheDeliveredHead()) {
            gaps.add("its request's head is not the delivered head");
        }
        return gaps;
    }

    private StageChange changing(TaskStage stage, Change change) {
        requireFrom(stage, EnumSet.of(DELIVERED, READY), CHANGING);
        return change(stage, CHANGING, change.cause().sentence());
    }

    private StageChange merged(TaskStage stage, RequestMerged merge) {
        requireFrom(stage, MERGEABLE, MERGED);
        return new StageChange(task, stage, MERGED, mergedReasonOf(merge), merge.commitsAfterTheMergedHead());
    }

    private static String mergedReasonOf(RequestMerged merge) {
        String mergedAt = "merged at " + merge.mergedHead();
        return merge.commitsAfterTheMergedHead().isEmpty() ? mergedAt : mergedAt + "; " + notLandedOf(merge.commitsAfterTheMergedHead());
    }

    private static String notLandedOf(List<BranchCommit> commits) {
        String count = commits.size() == 1 ? "1 commit" : commits.size() + " commits";
        return count + " not landed: " + String.join(", ", commits.stream().map(BranchCommit::shortId).toList());
    }

    private StageChange landed(TaskStage stage, LandingObserved landing) {
        requireFrom(stage, EnumSet.of(MERGED), LANDED);
        refuseAnyOf(stage, landingGapsOf(landing));
        return change(stage, LANDED, "every commit up to the merged head is in the base");
    }

    private static List<String> landingGapsOf(LandingObserved landing) {
        List<String> gaps = new ArrayList<>();
        if (!landing.landedCommitInTheBase()) {
            gaps.add("the commit the merge landed is not in the base yet");
        }
        if (!landing.commitsAfterTheMergedHead().isEmpty()) {
            gaps.add(notLandedOf(landing.commitsAfterTheMergedHead()));
        }
        return gaps;
    }

    private StageChange retired(TaskStage stage, CleanUp cleanUp) {
        requireFrom(stage, EnumSet.of(cleanedUpFrom()), RETIRED);
        refuseAnyOf(stage, cleanUpGapsOf(cleanUp));
        return change(stage, RETIRED, "cleaned up");
    }

    private TaskStage cleanedUpFrom() {
        return kind == TaskKind.SCOUT ? DELIVERED : LANDED;
    }

    private List<String> cleanUpGapsOf(CleanUp cleanUp) {
        List<String> gaps = new ArrayList<>();
        cleanUp.leftovers().stream().sorted().forEach(leftover -> gaps.add(leftover.phrase() + " is not retired"));
        if (kind == TaskKind.SCOUT && !cleanUp.reportFiled()) {
            gaps.add(REPORT_NOT_FILED);
        }
        return gaps;
    }

    private StageChange closedByItsAnswer(TaskStage stage, CaptainItemAnswered answered) {
        requireFrom(stage, EnumSet.of(QUEUED), RETIRED);
        refuseAnyOf(stage, answerGapsOf(answered));
        return change(stage, RETIRED, "its decision is answered");
    }

    private List<String> answerGapsOf(CaptainItemAnswered answered) {
        List<String> gaps = new ArrayList<>();
        if (kind != TaskKind.CAPTAIN) {
            gaps.add("only a captain item is closed by the answer to its decision");
        }
        gaps.addAll(answered.holdsAfterTheAnswer().reasons());
        return gaps;
    }

    private StageChange dropped(TaskStage stage, Drop drop) {
        requireFrom(stage, DROPPABLE, DROPPED);
        refuseAnyOf(stage, dropGapsOf(drop));
        return change(stage, DROPPED, "dropped on the captain's word");
    }

    private static List<String> dropGapsOf(Drop drop) {
        if (drop.unlandedWork().isEmpty() || drop.discardingWork()) {
            return List.of();
        }
        return List.of(String.join("; ", drop.unlandedWork()) + ", and the captain's word does not discard it");
    }
}
