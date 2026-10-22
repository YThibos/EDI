package io.github.ythibos.edi.application.dispatch;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.InboxStore;
import io.github.ythibos.edi.adapter.store.PipelineRunStore;
import io.github.ythibos.edi.application.brief.BriefFacts;
import io.github.ythibos.edi.domain.OperationalText;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.landing.BranchCommit;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;

@Component
public class ResumeNotes {

    private static final int LONGEST_STATUS = 160;
    private static final String HEAD = "HEAD";
    private static final List<String> CARRYING_ON = List.of(
            "## Carrying on",
            "",
            "Run `edi inbox` first, then read the brief.",
            "Compare the commits and the uncommitted changes with the plan before you change anything.",
            "Carry on from the first step that is not done: keep the committed work, and finish or deliberately undo the uncommitted changes.",
            "Report `working` with the step you take over at.");

    private final Git git;
    private final InboxStore inbox;
    private final DecisionStore decisions;
    private final PipelineRunStore pipelineRuns;
    private final ResearchBeforeImplementation research;
    private final TransactionTemplate transaction;

    public ResumeNotes(Git git, InboxStore inbox, DecisionStore decisions, PipelineRunStore pipelineRuns, ResearchBeforeImplementation research,
            TransactionTemplate transaction) {
        this.git = git;
        this.inbox = inbox;
        this.decisions = decisions;
        this.pipelineRuns = pipelineRuns;
        this.research = research;
        this.transaction = transaction;
    }

    public String noteFor(Worker earlier, Path worktree, BranchName branch, String because) {
        TaskId task = earlier.id().task();
        return OperationalText.marked("resumed after " + because + ": your copy is on " + branch + " at " + headOf(worktree) + "; your last status was "
                + lastStatusOf(earlier) + "; answered since you started: " + answeredFor(task) + "; run edi inbox first, then carry on with the task");
    }

    public String handoverFor(BriefFacts fresh, Worker earlier, Path brief, String because) {
        TaskId task = fresh.task().id();
        return Stream.of(opening(fresh, earlier, because), theBranch(fresh), thePipeline(task), thePlan(task, brief), whereItStood(earlier), CARRYING_ON)
                .flatMap(List::stream)
                .collect(Collectors.joining("\n", "", "\n"));
    }

    private String headOf(Path worktree) {
        try {
            return git.headSummary(worktree);
        } catch (ProgramFailure unreadable) {
            return "a head EDI could not read";
        }
    }

    private static String lastStatusOf(Worker earlier) {
        String status = statusOf(earlier).replace('\n', ' ');
        return status.length() <= LONGEST_STATUS ? status : status.substring(0, LONGEST_STATUS) + "...";
    }

    private static String statusOf(Worker earlier) {
        if (earlier.lastStatus() == null) {
            return "none";
        }
        return earlier.lastStatus().report().state().word() + ": " + earlier.lastStatus().report().text();
    }

    private String answeredFor(TaskId task) {
        List<String> answered = transaction.execute(_ -> {
            List<String> found = new ArrayList<>(inbox.resolvingOf(task).stream().map(InboxMessage::resolves).map(Object::toString).toList());
            decisions.ofTask(task).filter(decision -> !decision.isOpen()).map(Decision::number).map(Object::toString).ifPresent(found::add);
            return found;
        });
        return answered.isEmpty() ? "nothing" : String.join(", ", answered);
    }

    private static List<String> opening(BriefFacts fresh, Worker earlier, String because) {
        return List.of("# Handover of " + fresh.task().id(), "",
                fresh.worker() + " takes over from " + earlier.id() + " in the same isolated copy, because " + because + ".",
                "Nothing of that conversation carries over: this handover, the brief and the copy are what you start from.", "");
    }

    private List<String> theBranch(BriefFacts fresh) {
        Path worktree = fresh.worktree();
        String baseRef = "origin/" + fresh.base();
        List<String> lines = new ArrayList<>(List.of("## The branch", "",
                "Your copy " + worktree + " is on " + fresh.dispatch().branch() + " at " + headOf(worktree) + ".", ""));
        lines.addAll(listed("Commits beyond " + baseRef + ", oldest first:", "No commits beyond " + baseRef + " yet.", commitsBeyond(worktree, baseRef)));
        lines.addAll(listed("Uncommitted changes:", "No uncommitted changes.", uncommittedIn(worktree)));
        return lines;
    }

    private static List<String> listed(String heading, String whenNone, List<String> items) {
        if (items.isEmpty()) {
            return List.of(whenNone, "");
        }
        List<String> lines = new ArrayList<>(List.of(heading, ""));
        items.forEach(item -> lines.add("- " + item));
        lines.add("");
        return lines;
    }

    private List<String> commitsBeyond(Path worktree, String baseRef) {
        try {
            return git.commitsBeyond(worktree, baseRef, HEAD).stream().map(ResumeNotes::commitLine).toList();
        } catch (ProgramFailure unreadable) {
            return List.of("EDI could not read them; run `git log " + baseRef + "..HEAD`.");
        }
    }

    private static String commitLine(BranchCommit commit) {
        return commit.shortId() + " " + commit.subject();
    }

    private List<String> uncommittedIn(Path worktree) {
        try {
            return git.uncommittedPaths(worktree);
        } catch (ProgramFailure unreadable) {
            return List.of("EDI could not read them; run `git status`.");
        }
    }

    private List<String> thePipeline(TaskId task) {
        String run = transaction.execute(_ -> pipelineRuns.latestOf(task)).map(ResumeNotes::describedRun)
                .orElse("No pipeline run is recorded for the task yet.");
        return List.of("## The pipeline", "", run, "");
    }

    private static String describedRun(PipelineRun run) {
        String step = run.activeStep() == null ? "" : ", at its " + run.activeStep() + " step";
        String mergeRequest = run.mergeRequestUrl() == null ? "" : ", for " + run.mergeRequestUrl();
        return "The latest pipeline run, " + run.id() + ", is " + run.status() + step + mergeRequest + ".";
    }

    private List<String> thePlan(TaskId task, Path brief) {
        List<String> lines = new ArrayList<>(List.of("## The plan", "", "The brief: " + brief + "."));
        research.planReportFor(task).ifPresent(report -> lines.add("The scout's plan, which the brief carries verbatim: " + report + "."));
        lines.add("");
        return lines;
    }

    private List<String> whereItStood(Worker earlier) {
        return List.of("## Where " + earlier.id() + " stood", "",
                "Its last status, in its own words:", "", "```text", statusOf(earlier), "```", "",
                "Answered since the task started: " + answeredFor(earlier.id().task()) + ".", "");
    }
}
