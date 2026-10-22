package io.github.ythibos.edi.application.dispatch;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.InboxStore;
import io.github.ythibos.edi.domain.OperationalText;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;

@Component
public class ResumeNotes {

    private static final int LONGEST_STATUS = 160;

    private final Git git;
    private final InboxStore inbox;
    private final DecisionStore decisions;
    private final TransactionTemplate transaction;

    public ResumeNotes(Git git, InboxStore inbox, DecisionStore decisions, TransactionTemplate transaction) {
        this.git = git;
        this.inbox = inbox;
        this.decisions = decisions;
        this.transaction = transaction;
    }

    public String noteFor(Worker earlier, Path worktree, BranchName branch, String because) {
        TaskId task = earlier.id().task();
        return OperationalText.marked("resumed after " + because + ": your copy is on " + branch + " at " + headOf(worktree) + "; your last status was "
                + lastStatusOf(earlier) + "; answered since you started: " + answeredFor(task) + "; run edi inbox first, then carry on with the task");
    }

    private String headOf(Path worktree) {
        try {
            return git.headSummary(worktree);
        } catch (ProgramFailure unreadable) {
            return "a head EDI could not read";
        }
    }

    private static String lastStatusOf(Worker earlier) {
        if (earlier.lastStatus() == null) {
            return "none";
        }
        String status = earlier.lastStatus().report().state().word() + ": " + earlier.lastStatus().report().text().replace('\n', ' ');
        return status.length() <= LONGEST_STATUS ? status : status.substring(0, LONGEST_STATUS) + "...";
    }

    private String answeredFor(TaskId task) {
        List<String> answered = transaction.execute(_ -> {
            List<String> found = new ArrayList<>(inbox.resolvingOf(task).stream().map(InboxMessage::resolves).map(Object::toString).toList());
            decisions.everyOneOfTask(task).stream().filter(decision -> !decision.isOpen()).map(Decision::number).map(Object::toString).forEach(found::add);
            return found;
        });
        return answered.isEmpty() ? "nothing" : String.join(", ", answered);
    }
}
