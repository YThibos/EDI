package io.github.ythibos.edi.cli;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalInt;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IParameterExceptionHandler;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Spec;

@Command(
        name = "edi",
        mixinStandardHelpOptions = true,
        scope = ScopeType.INHERIT,
        versionProvider = EdiVersion.class,
        description = "Talks to EDI's core for the captain, the primary session, workers and hooks.",
        synopsisSubcommandLabel = "COMMAND",
        subcommands = {
            UpCommand.class,
            DownCommand.class,
            HealthCommand.class,
            UpdateCommand.class,
            ReportCommand.class,
            SitrepCommand.class,
            ConsoleCommand.class,
            TaskCommand.class,
            BacklogCommand.class,
            HoldCommand.class,
            ReleaseCommand.class,
            DecisionCommand.class,
            ProjectCommand.class,
            DispatchCommand.class,
            BriefCommand.class,
            StatusCommand.class,
            InboxCommand.class,
            SteerCommand.class,
            WorkerCommand.class,
            WaitCommand.class,
            RecoverCommand.class,
            DeliverCommand.class,
            MergedCommand.class,
            CleanupCommand.class,
            BoardCommand.class,
            CaptainCommand.class,
            WorktreeCommand.class,
            MemoryCommand.class,
            PresenceCommand.class,
            PersonalityCommand.class,
            HookCommand.class,
            StatusLineCommand.class,
            PublishCheckCommand.class,
            RulesCommand.class,
            MigrateCommand.class})
public final class Edi implements Runnable {

    private static final String TOKEN_FILE = "EDI_TOKEN_FILE";
    private static final String WORKER = "EDI_WORKER";
    private static final String WORKER_ROLE = "worker";
    private static final String WORKER_TOKEN_FILE = "access.worker-token-file";
    private static final String ARGUMENTS = "command.arguments";

    private final Map<String, String> environment;
    private final InputStream input;
    private final StandardOutput output;

    @Spec
    private CommandSpec spec;

    private boolean tokenFileRefusalRecorded;

    public Edi(Map<String, String> environment, InputStream input) {
        this(environment, input, StandardOutput.OF_THIS_PROCESS);
    }

    Edi(Map<String, String> environment, InputStream input, StandardOutput output) {
        this.environment = environment;
        this.input = input;
        this.output = output;
    }

    public static void main(String[] arguments) {
        System.exit(commandLine(new Edi(System.getenv(), System.in)).execute(arguments));
    }

    static CommandLine commandLine(Edi edi) {
        CommandLine commandLine = new CommandLine(edi);
        IParameterExceptionHandler readingFailed = commandLine.getParameterExceptionHandler();
        return commandLine.setParameterExceptionHandler((unreadable, arguments) -> {
            new RuleFirings(edi).fired(ARGUMENTS, RuleFirings.REFUSED, unreadable.getMessage());
            return readingFailed.handleParseException(unreadable, arguments);
        });
    }

    @Override
    public void run() {
        throw new ParameterException(spec.commandLine(), "Name a command; edi --help lists them.");
    }

    InputStream input() {
        return input;
    }

    StandardOutput output() {
        return output;
    }

    Map<String, String> environment() {
        return environment;
    }

    String coreExecutable() {
        String named = environment.get("EDI_CORE");
        if (named != null && !named.isBlank()) {
            return named;
        }
        return ProcessHandle.current().info().command().map(Path::of).map(Path::getParent).map(directory -> directory.resolve("edi-core"))
                .filter(Files::isExecutable).map(Path::toString).orElse("edi-core");
    }

    Path checkout() {
        String checkout = environment.get("EDI_CHECKOUT");
        return checkout == null || checkout.isBlank() ? Path.of(System.getProperty("user.home"), "EDI") : Path.of(checkout);
    }

    Path home() {
        String home = environment.get("EDI_HOME");
        return home == null ? Path.of(System.getProperty("user.home"), ".edi") : Path.of(home);
    }

    boolean runsForAWorker() {
        return isSet(TOKEN_FILE) || runsInAWorkersWindow();
    }

    Path tokenFile() {
        if (isSet(TOKEN_FILE)) {
            return Path.of(environment.get(TOKEN_FILE));
        }
        if (runsInAWorkersWindow()) {
            String refusal = "This shell is " + whoseWindow() + ", yet " + TOKEN_FILE + " names no token file, so edi does not fall back"
                    + " to the home token: a worker calls the core only as itself.";
            recordTheTokenFileRefusal(refusal);
            throw new CoreUnavailable(refusal);
        }
        return home().resolve("state").resolve("core.token");
    }

    ClaudeSession claudeSession() {
        return ClaudeSession.ofEnvironment(environment);
    }

    CoreConnection core() {
        return CoreConnection.in(home(), tokenFile(), claudeSession());
    }

    OptionalInt paneWidth() {
        return TmuxPane.ofEnvironment(environment).map(TmuxPane::width).orElseGet(OptionalInt::empty);
    }

    private void recordTheTokenFileRefusal(String refusal) {
        if (!tokenFileRefusalRecorded) {
            tokenFileRefusalRecorded = true;
            new RuleFirings(this).firedWithoutTheCore(WORKER_TOKEN_FILE, RuleFirings.REFUSED, refusal);
        }
    }

    private boolean isSet(String variable) {
        String value = environment.get(variable);
        return value != null && !value.isBlank();
    }

    private boolean runsInAWorkersWindow() {
        return WORKER_ROLE.equals(environment.get("EDI_ROLE")) || isSet(WORKER) || isSet("EDI_TASK_ID");
    }

    private String whoseWindow() {
        return isSet(WORKER) ? "worker " + environment.get(WORKER) + "'s" : "a worker's";
    }
}
