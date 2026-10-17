package io.github.ythibos.edi.application.delivery;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.PipelineReviewStore;
import io.github.ythibos.edi.adapter.store.TaskCostStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.Pipeline;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.application.worker.WorkerContext;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.delivery.PipelineReview;
import io.github.ythibos.edi.domain.delivery.TaskCost;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.CleanupRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.ReviewCounted;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskCleanedUp;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskCostRecorded;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerRetired;
import io.github.ythibos.edi.domain.landing.LandedWork;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.worker.Worker;

@Service
public class CleanUpTask {

    private static final String REPORT_FILE = "report.md";
    private static final String BRANCHES = "refs/heads/";

    private final EdiHome home;
    private final TaskStore tasks;
    private final WorkerStore workers;
    private final TaskCostStore costs;
    private final PipelineReviewStore reviews;
    private final EventLog events;
    private final WorkerContext context;
    private final LandedWorkCheck landedWork;
    private final TaskCosts taskCosts;
    private final Pipeline pipeline;
    private final Git git;
    private final WorkerRuntime runtime;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public CleanUpTask(EdiHome home, TaskStore tasks, WorkerStore workers, TaskCostStore costs, PipelineReviewStore reviews, EventLog events,
            WorkerContext context, LandedWorkCheck landedWork, TaskCosts taskCosts, Pipeline pipeline, Git git, WorkerRuntime runtime, Clock clock,
            TransactionTemplate transaction) {
        this.home = home;
        this.tasks = tasks;
        this.workers = workers;
        this.costs = costs;
        this.reviews = reviews;
        this.events = events;
        this.context = context;
        this.landedWork = landedWork;
        this.taskCosts = taskCosts;
        this.pipeline = pipeline;
        this.git = git;
        this.runtime = runtime;
        this.clock = clock;
        this.transaction = transaction;
    }

    public Task cleanUp(TaskReference reference, Actor actor) {
        Workplace workplace = transaction.execute(_ -> context.of(tasks.get(reference).id()));
        workplace.task().requireReadyForCleanUp();
        requireNoWorkerAtWork(workplace);
        requireTheReportOfAScout(workplace);
        removeTheNestedRepositoriesOfAScout(workplace);
        requireLanded(workplace, landedWork.of(workplace), actor);
        TaskCost cost = taskCosts.countFor(workplace);
        PipelineReview review = reviewOf(workplace);
        retireTheCopy(workplace);
        return closed(workplace, cost, review, actor);
    }

    private void requireNoWorkerAtWork(Workplace workplace) {
        transaction.execute(_ -> workers.latestOf(workplace.task().id()))
                .filter(worker -> !worker.isRetired() && !worker.handedItsWorkOver() && runtime.isRunning(worker.window()))
                .ifPresent(working -> {
                    throw new Refusal(working.id() + " still works on " + workplace.task().id() + "; it is cleaned up once its work is handed over.");
                });
    }

    private void requireTheReportOfAScout(Workplace workplace) {
        if (workplace.task().intake().kind() != TaskKind.SCOUT) {
            return;
        }
        boolean doneAccepted = transaction.execute(_ -> workers.latestOf(workplace.task().id())).filter(Worker::handedItsWorkOver).isPresent();
        if (!doneAccepted || !Files.isRegularFile(workplace.taskDirectory().resolve(REPORT_FILE))) {
            throw new Refusal(workplace.task().id() + " is a scout, and a scout is cleaned up only after its done was accepted and its report "
                    + workplace.taskDirectory().resolve(REPORT_FILE) + " exists.");
        }
    }

    private void removeTheNestedRepositoriesOfAScout(Workplace workplace) {
        if (workplace.task().intake().kind() == TaskKind.SCOUT && Files.isDirectory(workplace.worktree())) {
            git.nestedRepositories(workplace.worktree()).forEach(nested -> deleted(workplace.worktree().resolve(nested)));
        }
    }

    private void requireLanded(Workplace workplace, LandedWork landed, Actor actor) {
        if (landed.isLanded()) {
            return;
        }
        transaction.executeWithoutResult(_ -> events.record(new CleanupRefused(workplace.task().id(), landed.findings()), actor, clock.now()));
        throw new Refusal(workplace.task().id() + " is not cleaned up, because its work is not landed: " + String.join("; ", landed.findings())
                + ". Nothing is discarded without the captain's word.");
    }

    private PipelineReview reviewOf(Workplace workplace) {
        return pipeline.reviewOf(workplace.project().projectClone(), workplace.dispatch().branch()).orElse(PipelineReview.unknown());
    }

    private void retireTheCopy(Workplace workplace) {
        List<Worker> incarnations = transaction.execute(_ -> workers.allOf(workplace.task().id()));
        incarnations.stream().filter(worker -> !worker.isRetired()).forEach(worker -> runtime.close(worker.window()));
        Path clone = workplace.project().projectClone();
        if (Files.isDirectory(workplace.worktree())) {
            git.removeWorktree(clone, workplace.worktree());
        }
        if (git.commitAt(clone, BRANCHES + workplace.dispatch().branch()).isPresent()) {
            git.deleteBranch(clone, workplace.dispatch().branch());
        }
        incarnations.forEach(worker -> deleted(home.workerState(worker.id())));
    }

    private Task closed(Workplace workplace, TaskCost cost, PipelineReview review, Actor actor) {
        return transaction.execute(_ -> {
            Instant now = clock.now();
            Task cleaned = tasks.get(workplace.task().id()).cleanedUp(now);
            workers.allOf(cleaned.id()).stream().filter(worker -> !worker.isRetired()).forEach(worker -> retired(worker, now, actor));
            tasks.update(cleaned);
            costs.save(cost);
            events.record(new TaskCostRecorded(cleaned.id(), cost), actor, now);
            reviews.save(cleaned.id(), review, now);
            events.record(new ReviewCounted(cleaned.id(), review), actor, now);
            events.record(new TaskCleanedUp(cleaned.id()), actor, now);
            return cleaned;
        });
    }

    private void retired(Worker worker, Instant now, Actor actor) {
        workers.update(worker.retired(now));
        events.record(new WorkerRetired(worker.id().task(), worker.id(), "its task was cleaned up"), actor, now);
    }

    private static void deleted(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> tree = Files.walk(directory)) {
            for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException undeletable) {
            throw new UncheckedIOException(undeletable);
        }
    }
}
