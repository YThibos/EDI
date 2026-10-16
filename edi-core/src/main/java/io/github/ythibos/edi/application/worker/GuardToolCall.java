package io.github.ythibos.edi.application.worker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.ToolCallRefused;
import io.github.ythibos.edi.domain.guard.GuardRefusal;
import io.github.ythibos.edi.domain.guard.GuardRule;
import io.github.ythibos.edi.domain.guard.ShellGuard;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Service
public class GuardToolCall {

    private static final Set<String> USUAL_BASES = Set.of("main", "master");

    private final WorkerContext context;
    private final ProjectRegistry registry;
    private final WorkerRuntime runtime;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public GuardToolCall(WorkerContext context, ProjectRegistry registry, WorkerRuntime runtime, EventLog events, Clock clock,
            TransactionTemplate transaction) {
        this.context = context;
        this.registry = registry;
        this.runtime = runtime;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public HookAnswer checkForWorker(WorkerId worker, ToolCall call) {
        Workplace workplace = transaction.execute(_ -> context.of(worker.task()));
        Optional<GuardRefusal> refusal = call.writesAFile()
                ? outsideTheWorkplace(call.writtenFile(), workplace)
                : shellRefusal(call, basesOf(workplace));
        refusal.ifPresent(refused -> transaction.executeWithoutResult(_ ->
                events.record(new ToolCallRefused(worker.task(), worker, refused.rule()), Actor.worker(worker), clock.now())));
        return refusal.map(GuardToolCall::denial).orElse(HookAnswer.PROCEED);
    }

    public HookAnswer checkForPrimary(ToolCall call) {
        return shellRefusal(call, everyRegisteredBase()).map(GuardToolCall::denial).orElse(HookAnswer.PROCEED);
    }

    private Optional<GuardRefusal> shellRefusal(ToolCall call, Set<String> bases) {
        return call.runsAShellCommand() ? new ShellGuard(runtime.socket(), bases).check(call.command()) : Optional.empty();
    }

    private static Set<String> basesOf(Workplace workplace) {
        Set<String> bases = new HashSet<>(USUAL_BASES);
        bases.add(workplace.base().name());
        Optional.ofNullable(workplace.project().base()).map(BaseBranch::name).ifPresent(bases::add);
        return bases;
    }

    private Set<String> everyRegisteredBase() {
        Set<String> bases = new HashSet<>(USUAL_BASES);
        registry.entries().stream().map(ProjectEntry::base).filter(Objects::nonNull).map(BaseBranch::name).forEach(bases::add);
        return bases;
    }

    private static Optional<GuardRefusal> outsideTheWorkplace(Path file, Workplace workplace) {
        Path written = realised(file);
        boolean inside = List.of(workplace.worktree(), workplace.taskDirectory()).stream().map(GuardToolCall::realised).anyMatch(written::startsWith);
        return inside ? Optional.empty() : Optional.of(new GuardRefusal(GuardRule.OUTSIDE_ISOLATED_COPY,
                "a worker writes only inside its isolated copy " + workplace.worktree() + " and its task directory "
                        + workplace.taskDirectory() + ", and " + file + " is outside both (I-07)."));
    }

    private static Path realised(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path existing = absolute;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return absolute;
        }
        try {
            return existing.toRealPath().resolve(existing.relativize(absolute));
        } catch (IOException unreadable) {
            return absolute;
        }
    }

    private static HookAnswer denial(GuardRefusal refusal) {
        return HookAnswer.denyTheTool("EDI's guard refuses this: " + refusal.reason());
    }
}
