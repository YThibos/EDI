package io.github.ythibos.edi.application.worker;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
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
import io.github.ythibos.edi.domain.authority.CaptainWordAct;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.ToolCallRefused;
import io.github.ythibos.edi.domain.guard.GuardRefusal;
import io.github.ythibos.edi.domain.guard.GuardRule;
import io.github.ythibos.edi.domain.guard.SecretReads;
import io.github.ythibos.edi.domain.guard.Secrets;
import io.github.ythibos.edi.domain.guard.SettingsWrites;
import io.github.ythibos.edi.domain.guard.ShellGuard;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Service
public class GuardToolCall {

    private static final Set<String> USUAL_BASES = Set.of("main", "master");
    private static final String CLAUDE_DIRECTORY = ".claude";
    private static final Map<GuardRule, CaptainWordAct> ALLOWED_ON_THE_CAPTAINS_WORD = Map.of(
            GuardRule.SECRET, CaptainWordAct.TOUCH_A_SECRET,
            GuardRule.UNLANDED_BRANCH_DELETION, CaptainWordAct.DELETE_UNLANDED_BRANCH);
    private static final GuardRefusal DIALOG_ANSWERED_AHEAD = new GuardRefusal(GuardRule.DIALOG_ANSWERED_AHEAD,
            "a dialog to the captain arrives with its answers already given, and only the captain answers it.");

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
        Optional<GuardRefusal> refusal = refusalForWorker(worker, call);
        refusal.ifPresent(refused -> transaction.executeWithoutResult(_ ->
                events.record(new ToolCallRefused(worker.task(), worker, refused.rule()), Actor.worker(worker), clock.now())));
        return refusal.map(GuardToolCall::denial).orElse(HookAnswer.PROCEED);
    }

    public HookAnswer checkForPrimary(ToolCall call, CallingSession calling) {
        return refusalForPrimary(call)
                .filter(refused -> !allowedOnTheCaptainsWord(refused, calling))
                .map(GuardToolCall::denialToThePrimary)
                .orElse(HookAnswer.PROCEED);
    }

    private Optional<GuardRefusal> refusalForWorker(WorkerId worker, ToolCall call) {
        if (call.readsAFile()) {
            return secretRead(call.file(), secretsKeptFromAWorkersRead());
        }
        if (call.searchesForFiles()) {
            return searchReachingASecret(call);
        }
        Workplace workplace = transaction.execute(_ -> context.of(worker.task()));
        return call.writesAFile()
                ? outsideTheWorkplace(call.file(), workplace)
                        .or(() -> SettingsWrites.refusalForWriting(call.file().toString(), underTheSettingsOf(workplace, call)))
                : shellRefusal(call, basesOf(workplace), secretsKeptFromAWorkersShell(call), true)
                        .or(() -> settingsWrittenByTheShell(call, workplace));
    }

    private Optional<GuardRefusal> refusalForPrimary(ToolCall call) {
        if (call.asksTheCaptainWithItsAnswersGiven()) {
            return Optional.of(DIALOG_ANSWERED_AHEAD);
        }
        return call.readsAFile()
                ? secretRead(call.file(), secretsKeptFromThePrimary())
                : shellRefusal(call, everyRegisteredBase(), secretsKeptFromThePrimary(), false);
    }

    private static Optional<GuardRefusal> secretRead(Path file, Secrets secrets) {
        return SecretReads.refusalForReading(file.toString(), secrets)
                .or(() -> SecretReads.refusalForReading(RealPaths.of(file).toString(), secrets));
    }

    private Optional<GuardRefusal> shellRefusal(ToolCall call, Set<String> bases, Secrets secrets, boolean forAWorker) {
        if (!call.runsAShellCommand()) {
            return Optional.empty();
        }
        Set<String> unlandedBranches = transaction.execute(_ -> dispatches.branchesOfOpenTasks());
        return new ShellGuard(runtime.socket(), bases, unlandedBranches, secrets, forAWorker, socketPath -> realisedFrom(call.workingDirectory(), socketPath))
                .check(call.command());
    }

    private Optional<GuardRefusal> searchReachingASecret(ToolCall call) {
        Path directory = call.searchedDirectory();
        return SecretReads.refusalForSearching(
                () -> directory != null && secretFiles().stream().anyMatch(secret -> SecretSearches.reach(directory, call.pattern(), secret)));
    }

    private Secrets secretsKeptFromAWorkersRead() {
        return new Secrets(secretFileEndings(), false);
    }

    private Secrets secretsKeptFromAWorkersShell(ToolCall call) {
        return new Secrets(List.of(homeTokenEnding()), false, pathsFrom(call));
    }

    private Secrets secretsKeptFromThePrimary() {
        return new Secrets(secretFileEndings(), true);
    }

    private ShellWordPaths pathsFrom(ToolCall call) {
        return new ShellWordPaths(call.workingDirectory(), Path.of(System.getProperty("user.home")), System.getProperty("user.name"), home.root());
    }

    private List<Path> secretFiles() {
        return Stream.concat(Stream.of(home.coreTokenFile()), keysFile().stream()).toList();
    }

    private List<String> secretFileEndings() {
        return Stream.concat(Stream.of(homeTokenEnding()), keysFileEnding().stream()).toList();
    }

    private Optional<String> keysFileEnding() {
        return keysFile().map(file -> "/" + file.getFileName());
    }

    private Optional<Path> keysFile() {
        try {
            return Optional.ofNullable(machine.tracker().credentials()).map(Credentials::keysFile);
        } catch (InvalidConfiguration unreadable) {
            return Optional.empty();
        }
    }

    private String homeTokenEnding() {
        Path token = home.coreTokenFile();
        return "/" + token.getParent().getFileName() + "/" + token.getFileName();
    }

    private boolean allowedOnTheCaptainsWord(GuardRefusal refusal, CallingSession calling) {
        return Optional.ofNullable(ALLOWED_ON_THE_CAPTAINS_WORD.get(refusal.rule()))
                .flatMap(act -> captainsWord.onTheHome(calling, act))
                .isPresent();
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
        Path written = RealPaths.of(file);
        boolean inside = List.of(workplace.worktree(), workplace.taskDirectory()).stream().map(RealPaths::of).anyMatch(written::startsWith);
        return inside ? Optional.empty() : Optional.of(new GuardRefusal(GuardRule.OUTSIDE_ISOLATED_COPY,
                "a worker writes only inside its isolated copy " + workplace.worktree() + " and its task directory "
                        + workplace.taskDirectory() + ", and " + file + " is outside both (I-07)."));
    }

    private static Predicate<String> underTheSettingsOf(Workplace workplace, ToolCall call) {
        Path settings = RealPaths.of(workplace.worktree()).resolve(CLAUDE_DIRECTORY);
        Path written = workplace.worktree().toAbsolutePath().normalize().resolve(CLAUDE_DIRECTORY);
        return word -> {
            Path path = pathOf(word, call.workingDirectory());
            return path != null && (path.toAbsolutePath().normalize().startsWith(written) || RealPaths.of(path).startsWith(settings));
        };
    }

    private static Optional<GuardRefusal> settingsWrittenByTheShell(ToolCall call, Workplace workplace) {
        return call.runsAShellCommand() ? SettingsWrites.refusalFor(call.command(), underTheSettingsOf(workplace, call)) : Optional.empty();
    }

    private static Path pathOf(String word, Path workingDirectory) {
        try {
            Path path = Path.of(word);
            return path.isAbsolute() || workingDirectory == null ? path : workingDirectory.resolve(path);
        } catch (InvalidPathException unreadable) {
            return null;
        }
    }

    private static String realisedFrom(Path workingDirectory, String written) {
        try {
            Path path = Path.of(written);
            return RealPaths.of(workingDirectory == null ? path : workingDirectory.resolve(path)).toString();
        } catch (InvalidPathException unreadable) {
            return written;
        }
    }

    private static HookAnswer denial(GuardRefusal refusal) {
        return HookAnswer.denyTheTool("The guard refuses this: " + refusal.reason());
    }

    private static HookAnswer denialToThePrimary(GuardRefusal refusal) {
        String remedy = Optional.ofNullable(ALLOWED_ON_THE_CAPTAINS_WORD.get(refusal.rule())).map(GuardToolCall::afterTheCaptainsWord).orElse("");
        return HookAnswer.denyTheTool("The guard refuses this: " + refusal.reason() + remedy);
    }

    private static String afterTheCaptainsWord(CaptainWordAct act) {
        return " The captain's word in this session lets the primary run it: record it with edi captain said --message <n>,"
                + " or have the captain choose a dialog option that opens with " + act.word() + ".";
    }
}
