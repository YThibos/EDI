package io.github.ythibos.edi.application.fleet;

import java.nio.file.Files;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.registry.UnreadableRegistry;
import io.github.ythibos.edi.adapter.store.BoardStore;
import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.DeliveryStore;
import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.PipelineRunStore;
import io.github.ythibos.edi.application.tracker.Trackers;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.dispatch.Dispatch;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;

@Service
public class TaskLinksQuery {

    public static final String TASK_PAGES = "/api/tasks/";
    static final String REPORT = "report.md";

    private final EdiHome home;
    private final ProjectRegistry registry;
    private final Trackers trackers;
    private final DispatchStore dispatches;
    private final DeliveryStore deliveries;
    private final PipelineRunStore pipelineRuns;
    private final BoardStore boards;
    private final DecisionStore decisions;
    private final TransactionTemplate transaction;

    public TaskLinksQuery(EdiHome home, ProjectRegistry registry, Trackers trackers, DispatchStore dispatches, DeliveryStore deliveries,
            PipelineRunStore pipelineRuns, BoardStore boards, DecisionStore decisions, TransactionTemplate transaction) {
        this.home = home;
        this.registry = registry;
        this.trackers = trackers;
        this.dispatches = dispatches;
        this.deliveries = deliveries;
        this.pipelineRuns = pipelineRuns;
        this.boards = boards;
        this.decisions = decisions;
        this.transaction = transaction;
    }

    public Map<TaskId, TaskLinks> linksOf(Collection<Task> tasks) {
        return transaction.execute(_ -> {
            Map<TaskId, TaskLinks> links = new LinkedHashMap<>();
            tasks.forEach(task -> links.computeIfAbsent(task.id(), _ -> linksOf(task)));
            return links;
        });
    }

    private TaskLinks linksOf(Task task) {
        Optional<ProjectEntry> project = task.intake().projects().stream().findFirst().flatMap(this::registered);
        Optional<PipelineRun> run = pipelineRuns.latestOf(task.id());
        return new TaskLinks(
                task.intake().workKey() == null ? null : task.intake().workKey().toString(),
                project.flatMap(entry -> task.intake().workKey() == null ? Optional.empty() : trackers.browseAddressOf(entry, task.intake().workKey()))
                        .orElse(null),
                deliveries.of(task.id()).map(delivery -> requestOf(delivery, run)).orElse(null),
                dispatches.of(task.id()).map(dispatch -> branchOf(dispatch, project)).orElse(null),
                boards.latestOf(task.id()).map(Board::link).orElse(null),
                pageOf(task, REPORT, "report"),
                decisions.everyOneOfTask(task.id()).stream().map(decision -> decision.number().toString()).toList());
    }

    private Optional<ProjectEntry> registered(ProjectName project) {
        try {
            return Optional.of(registry.entryOf(project));
        } catch (Refusal | UnreadableRegistry unregistered) {
            return Optional.empty();
        }
    }

    private static TaskLinks.Request requestOf(Delivery delivery, Optional<PipelineRun> run) {
        return new TaskLinks.Request(delivery.address().url(), delivery.address().number(), delivery.state().word(),
                run.filter(PipelineRun::isRunning).map(PipelineRun::activeStep).orElse(null));
    }

    private static TaskLinks.Branch branchOf(Dispatch dispatch, Optional<ProjectEntry> project) {
        String branch = dispatch.branch().toString();
        return new TaskLinks.Branch(branch, project.map(ProjectEntry::forge).map(forge -> forge.repository().branchUrl(branch)).orElse(null));
    }

    private String pageOf(Task task, String file, String page) {
        return Files.isRegularFile(home.taskDirectory(task.id()).resolve(file)) ? TASK_PAGES + task.id().value() + "/pages/" + page : null;
    }
}
