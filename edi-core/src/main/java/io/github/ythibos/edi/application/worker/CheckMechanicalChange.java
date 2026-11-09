package io.github.ythibos.edi.application.worker;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.build.ProjectBuild;
import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.RuleFirings;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.delivery.DeliverAcceptedWork;
import io.github.ythibos.edi.application.dispatch.LaunchWorker;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.MechanicalChangeAccepted;
import io.github.ythibos.edi.domain.event.DomainEvent.MechanicalChangeRefused;
import io.github.ythibos.edi.domain.mechanical.ExpectedChange;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.rule.RuleFired;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Service
public class CheckMechanicalChange {

    private static final String VERIFY_LOG = "mechanical-verify.log";

    private final WorkerStore workers;
    private final WorkerContext context;
    private final Git git;
    private final ProjectBuild build;
    private final WorkerRuntime runtime;
    private final LaunchWorker launcher;
    private final DeliverAcceptedWork acceptedWork;
    private final EventLog events;
    private final RuleFirings firings;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public CheckMechanicalChange(WorkerStore workers, WorkerContext context, Git git, ProjectBuild build, WorkerRuntime runtime,
            LaunchWorker launcher, DeliverAcceptedWork acceptedWork, EventLog events, RuleFirings firings, Clock clock, TransactionTemplate transaction) {
        this.workers = workers;
        this.context = context;
        this.git = git;
        this.build = build;
        this.runtime = runtime;
        this.launcher = launcher;
        this.acceptedWork = acceptedWork;
        this.events = events;
        this.firings = firings;
        this.clock = clock;
        this.transaction = transaction;
    }

    public List<WorkerId> awaitingTheirCheck() {
        return transaction.execute(_ -> workers.serving().stream().filter(Worker::awaitsItsDoneCheck).map(Worker::id).toList());
    }

    public void check(WorkerId id) {
        Workplace workplace = transaction.execute(_ -> context.of(id.task()));
        Optional<String> refusal = refusalOf(workplace);
        if (refusal.isEmpty()) {
            accept(id);
            acceptedWork.deliver(id.task());
        } else {
            refuse(id, refusal.get());
            handToAnImplementationWorker(id, workplace, refusal.get());
        }
    }

    private Optional<String> refusalOf(Workplace workplace) {
        return refusalOfTheDiff(workplace).or(() -> refusalOfTheBuild(workplace));
    }

    private Optional<String> refusalOfTheDiff(Workplace workplace) {
        ExpectedChange expected = workplace.dispatch().expectedChange();
        Set<String> changed = git.changedFiles(workplace.worktree(), workplace.baseRef());
        List<String> beyond = expected.filesBeyond(changed);
        if (!beyond.isEmpty()) {
            return Optional.of("the change touches " + beyond + ", which the brief did not name");
        }
        if (expected.isAbsentFrom(changed)) {
            return Optional.of("the change touches none of " + expected.files() + ", the files the brief named");
        }
        return Optional.empty();
    }

    private Optional<String> refusalOfTheBuild(Workplace workplace) {
        Path log = workplace.taskDirectory().resolve(VERIFY_LOG);
        if (!build.verifies(workplace.worktree(), workplace.project().verify(), log)) {
            return Optional.of("the verify command failed; its output is in " + log);
        }
        return Optional.empty();
    }

    private void accept(WorkerId id) {
        transaction.executeWithoutResult(_ -> {
            workers.update(WorkerLookup.serving(workers, id).doneChecked(true));
            events.record(new MechanicalChangeAccepted(id.task(), id), Actor.CORE, clock.now());
        });
    }

    private void refuse(WorkerId id, String because) {
        Worker refused = transaction.execute(_ -> {
            Instant now = clock.now();
            Worker checked = WorkerLookup.serving(workers, id).doneChecked(false);
            workers.update(checked);
            events.record(new MechanicalChangeRefused(id.task(), id, because), Actor.CORE, now);
            firings.record(RuleFired.refused(Rule.CHANGE_MATCHES_ITS_BRIEF, Actor.worker(id), id.task(), because), now);
            return checked;
        });
        runtime.close(refused.window());
    }

    private void handToAnImplementationWorker(WorkerId id, Workplace workplace, String because) {
        launcher.launch(workplace.dispatch().asImplementationBecause(because), "the mechanical change of " + id + " was refused: " + because, Actor.CORE);
    }
}
