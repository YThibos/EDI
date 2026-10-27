package io.github.ythibos.edi.application.worker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.home.InvalidConfiguration;
import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.home.TrackerSettings.Credentials;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.application.authority.CaptainsWordEvidence;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.ToolCallRefused;
import io.github.ythibos.edi.domain.guard.GuardRefusal;
import io.github.ythibos.edi.domain.guard.GuardRule;
import io.github.ythibos.edi.domain.guard.Secrets;
import io.github.ythibos.edi.domain.guard.ShellGuard;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Service
public class GuardToolCall {

    private static final Set<String> USUAL_BASES = Set.of("main", "master");
    private static final Set<GuardRule> ALLOWED_ON_THE_CAPTAINS_WORD = Set.of(GuardRule.SECRET, GuardRule.UNLANDED_BRANCH_DELETION);
    private static final String AFTER_THE_CAPTAINS_WORD = " The captain's word in this session lets the primary run it: record it with"
            + " edi captain said --text \"<their words>\".";

    private final EdiHome home;
    private final WorkerContext context;
    private final ProjectRegistry registry;
    private final DispatchStore dispatches;
    private final MachineProfile machine;
    private final CaptainsWordEvidence captainsWord;
    private final WorkerRuntime runtime;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public GuardToolCall(EdiHome home, WorkerContext context, ProjectRegistry registry, DispatchStore dispatches, MachineProfile machine,
            CaptainsWordEvidence captainsWord, WorkerRuntime runtime, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.home = home;
        this.context = context;
        this.registry = registry;
        this.dispatches = dispatches;
        this.machine = machine;
        this.captainsWord = captainsWord;
        this.runtime = runtime;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public HookAnswer checkForWorker(WorkerId worker, ToolCall call) {
        Workplace workplace = transaction.execute(_ -> context.of(worker.task()));
        Optional<GuardRefusal> refusal = call.writesAFile()
                ? outsideTheWorkplace(call.writtenFile(), workplace)
                : shellRefusal(call, basesOf(workplace), secretsKeptFromWorkers());
        refusal.ifPresent(refused -> transaction.executeWithoutResult(_ ->
                events.record(new ToolCallRefused(worker.task(), worker, refused.rule()), Actor.worker(worker), clock.now())));
        return refusal.map(GuardToolCall::denial).orElse(HookAnswer.PROCEED);
    }

    public HookAnswer checkForPrimary(ToolCall call, CallingSession calling) {
        return shellRefusal(call, everyRegisteredBase(), secretsKeptFromThePrimary())
                .filter(refused -> !allowedOnTheCaptainsWord(refused, calling))
                .map(GuardToolCall::denialToThePrimary)
                .orElse(HookAnswer.PROCEED);
    }

    private Optional<GuardRefusal> shellRefusal(ToolCall call, Set<String> bases, Secrets secrets) {
        if (!call.runsAShellCommand()) {
            return Optional.empty();
        }
        Set<String> unlandedBranches = transaction.execute(_ -> dispatches.branchesOfOpenTasks());
        return new ShellGuard(runtime.socket(), bases, unlandedBranches, secrets).check(call.command());
    }

    private Secrets secretsKeptFromWorkers() {
        return new Secrets(List.of(homeTokenEnding()), false);
    }

    private Secrets secretsKeptFromThePrimary() {
        return new Secrets(Stream.concat(Stream.of(homeTokenEnding()), keysFileEnding().stream()).toList(), true);
    }

    private Optional<String> keysFileEnding() {
        try {
            return Optional.ofNullable(machine.tracker().credentials()).map(Credentials::keysFile).map(file -> "/" + file.getFileName());
        } catch (InvalidConfiguration unreadable) {
            return Optional.empty();
        }
    }

    private String homeTokenEnding() {
        Path token = home.coreTokenFile();
        return "/" + token.getParent().getFileName() + "/" + token.getFileName();
    }

    private boolean allowedOnTheCaptainsWord(GuardRefusal refusal, CallingSession calling) {
        return ALLOWED_ON_THE_CAPTAINS_WORD.contains(refusal.rule()) && captainsWord.onTheHome(calling).isPresent();
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
        return HookAnswer.denyTheTool("The guard refuses this: " + refusal.reason());
    }

    private static HookAnswer denialToThePrimary(GuardRefusal refusal) {
        String remedy = ALLOWED_ON_THE_CAPTAINS_WORD.contains(refusal.rule()) ? AFTER_THE_CAPTAINS_WORD : "";
        return HookAnswer.denyTheTool("The guard refuses this: " + refusal.reason() + remedy);
    }
}
