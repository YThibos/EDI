package io.github.ythibos.edi.cli;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalInt;

import picocli.CommandLine;
import picocli.CommandLine.Command;
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
            HealthCommand.class,
            ReportCommand.class,
            SitrepCommand.class,
            TaskCommand.class,
            BacklogCommand.class,
            HoldCommand.class,
            ReleaseCommand.class,
            DecisionCommand.class,
            ProjectCommand.class,
            PublishCheckCommand.class,
            MigrateCommand.class})
public final class Edi implements Runnable {

    private final Map<String, String> environment;
    private final InputStream input;

    @Spec
    private CommandSpec spec;

    public Edi(Map<String, String> environment, InputStream input) {
        this.environment = environment;
        this.input = input;
    }

    public static void main(String[] arguments) {
        System.exit(new CommandLine(new Edi(System.getenv(), System.in)).execute(arguments));
    }

    @Override
    public void run() {
        throw new ParameterException(spec.commandLine(), "Name a command; edi --help lists them.");
    }

    InputStream input() {
        return input;
    }

    Path home() {
        String home = environment.get("EDI_HOME");
        return home == null ? Path.of(System.getProperty("user.home"), ".edi") : Path.of(home);
    }

    OptionalInt paneWidth() {
        return TmuxPane.ofEnvironment(environment).map(TmuxPane::width).orElseGet(OptionalInt::empty);
    }
}
