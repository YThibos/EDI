package io.github.ythibos.edi.domain.landing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.ythibos.edi.domain.authority.CaptainWordAct;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionOption;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.Recommendation;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.task.TaskId;

public record WorkAfterTheMergedHead(TaskId task, MergeRequestView request, List<BranchCommit> commits) {

    private static final OptionKey FOLLOW_UP = new OptionKey('A');
    private static final OptionKey DISCARD = new OptionKey('B');

    public WorkAfterTheMergedHead {
        Objects.requireNonNull(task, "Work after a merge is the work of a task.");
        Objects.requireNonNull(request, () -> "The work " + task + " did after its merge follows a merged request.");
        commits = List.copyOf(commits);
        if (commits.isEmpty()) {
            throw new IllegalArgumentException("Work after the merge of " + task + " names at least one commit after the merged head.");
        }
    }

    public static boolean isTheFollowUp(Decision decision) {
        return decision.card().title().equals(titleFor(decision.task()));
    }

    public static boolean choseTheFollowUp(Decision decision) {
        return isTheFollowUp(decision) && decision.answer() != null && FOLLOW_UP.equals(decision.answer().option());
    }

    public DecisionCard card() {
        return new DecisionCard(titleFor(task), context(), options(), recommendation());
    }

    private static String titleFor(TaskId task) {
        return "Deliver the commits " + task + " made after its merge through a follow-up task?";
    }

    private String context() {
        List<String> lines = new ArrayList<>();
        lines.add("The forge merged " + request.address() + " at " + request.headCommit() + ", while " + task + " went on: " + countOfCommits()
                + " after the merged head " + (commits.size() == 1 ? "is" : "are") + " not in " + request.targetBranch() + ":");
        commits.forEach(commit -> lines.add(line(commit)));
        lines.add(task + " stays merged, and its clean-up is refused, until they land or are discarded.");
        return String.join("\n", lines);
    }

    private String countOfCommits() {
        return commits.size() == 1 ? "1 commit" : commits.size() + " commits";
    }

    private static String line(BranchCommit commit) {
        return "- " + commit.shortId() + " \"" + commit.subject() + "\"";
    }

    private List<DecisionOption> options() {
        return List.of(
                new DecisionOption(FOLLOW_UP, "Deliver them through a follow-up task that carries them from " + request.sourceBranch() + " onto a new branch from "
                        + request.targetBranch() + " and lands by its own merged head; " + task + "'s copy of them is discarded once the follow-up task's branch"
                        + " holds them on the forge", CaptainWordAct.DISCARD_WORK),
                new DecisionOption(DISCARD, "Discard them: retire " + task + " with its copy and branch, and lose those commits",
                        CaptainWordAct.DISCARD_WORK));
    }

    private Recommendation recommendation() {
        return new Recommendation(FOLLOW_UP, "they are work done for the task after the request was merged, and discarding them outright cannot be undone");
    }
}
