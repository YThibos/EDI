package io.github.ythibos.edi.cli;

import java.nio.file.Path;
import java.util.Map;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

@Command(
        name = "edi",
        mixinStandardHelpOptions = true,
        versionProvider = EdiVersion.class,
        description = "Talks to EDI's core for the captain, the primary session, workers and hooks.",
        synopsisSubcommandLabel = "COMMAND",
        subcommands = HealthCommand.class)
public final class Edi implements Runnable {

    private final Map<String, String> environment;

    @Spec
    private CommandSpec spec;

    public Edi(Map<String, String> environment) {
        this.environment = environment;
    }

    public static void main(String[] arguments) {
        System.exit(new CommandLine(new Edi(System.getenv())).execute(arguments));
    }

    @Override
    public void run() {
        throw new ParameterException(spec.commandLine(), "Name a command; edi --help lists them.");
    }

    Path home() {
        String home = environment.get("EDI_HOME");
        return home == null ? Path.of(System.getProperty("user.home"), ".edi") : Path.of(home);
    }
}
