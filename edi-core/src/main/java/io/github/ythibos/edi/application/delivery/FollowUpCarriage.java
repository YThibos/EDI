package io.github.ythibos.edi.application.delivery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.dispatch.Dispatch;
import io.github.ythibos.edi.domain.landing.BranchCommit;
import io.github.ythibos.edi.domain.landing.FollowUpBranch;
import io.github.ythibos.edi.domain.landing.WorkAfterTheMergedHead;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.task.CaptainsWord;

@Component
public class FollowUpCarriage {

    private final DecisionStore decisions;
    private final DispatchStore dispatches;
    private final LandedWorkCheck landedWork;
    private final TransactionTemplate transaction;

    public FollowUpCarriage(DecisionStore decisions, DispatchStore dispatches, LandedWorkCheck landedWork, TransactionTemplate transaction) {
        this.decisions = decisions;
        this.dispatches = dispatches;
        this.landedWork = landedWork;
        this.transaction = transaction;
    }

    public void requireCarriedWhenFollowedUp(Workplace workplace, CaptainsWord word, List<BranchCommit> unlanded) {
        if (unlanded.isEmpty()) {
            return;
        }
        followUpChosenBy(workplace, word).ifPresent(followUp -> requireCarried(workplace, unlanded, followUp.raisedAt()));
    }

    private Optional<Decision> followUpChosenBy(Workplace workplace, CaptainsWord word) {
        return transaction.execute(_ -> decisions.everyOneOfTask(workplace.task().id())).stream()
                .filter(WorkAfterTheMergedHead::choseTheFollowUp)
                .filter(decision -> decision.answer().verbatim().equals(word.verbatim()))
                .findFirst();
    }

    private void requireCarried(Workplace workplace, List<BranchCommit> unlanded, Instant raisedAt) {
        List<FollowUpBranch> followUps = followUpBranchesSince(workplace, raisedAt);
        if (followUps.stream().noneMatch(branch -> branch.carriesAll(unlanded))) {
            throw notCarried(workplace, unlanded, followUps);
        }
    }

    private List<FollowUpBranch> followUpBranchesSince(Workplace workplace, Instant raisedAt) {
        return transaction.execute(_ -> dispatches.ofOtherTasksIn(workplace.project().project().name(), workplace.task().id())).stream()
                .filter(dispatch -> !dispatch.requestedAt().isBefore(raisedAt))
                .map(Dispatch::branch)
                .flatMap(branch -> landedWork.followUpBranch(workplace, branch).stream())
                .toList();
    }

    private static Refusal notCarried(Workplace workplace, List<BranchCommit> unlanded, List<FollowUpBranch> followUps) {
        String looked = followUps.isEmpty()
                ? "no task dispatched since the decision has pushed its branch to the forge"
                : "neither " + String.join(" nor ", followUps.stream().map(FollowUpBranch::ref).toList()) + " holds them";
        return new Refusal(Rule.LANDED_BEFORE_DISCARD, workplace.task().id() + "'s work is not discarded yet: the captain chose to deliver "
                + String.join(", ", unlanded.stream().map(BranchCommit::shortId).toList())
                + " through a follow-up task, and " + looked + ", by patch or by content. Push the follow-up task's branch first.");
    }
}
