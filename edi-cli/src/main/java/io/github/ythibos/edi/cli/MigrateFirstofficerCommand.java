package io.github.ythibos.edi.cli;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

@Command(name = "firstofficer", description = "Imports a firstofficer home into EDI: its backlog, holds and decisions, its memory and configuration, and"
        + " an archive of its data; the old home is only read, and --dry-run prints the plan and changes nothing.")
final class MigrateFirstofficerCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Spec
    private CommandSpec spec;

    @Option(names = "--from", required = true, paramLabel = "HOME", description = "The firstofficer home to read, such as ~/firstofficer.")
    private Path from;

    @Option(names = "--dry-run", description = "Print the plan and what it would change in this EDI home, without importing anything.")
    private boolean dryRun;

    @Option(names = "--overwrite", description = "Import into an EDI home that already holds work or files of its own: EDI's tasks and decisions stay,"
            + " and each file the import replaces is kept as <file>.before-migration.")
    private boolean overwrite;

    @Override
    public Integer call() {
        if (dryRun && overwrite) {
            throw new ParameterException(spec.commandLine(), "A dry run changes nothing, so it takes no --overwrite.");
        }
        String oldHome = from.toAbsolutePath().normalize().toString();
        return dryRun
                ? core.get("/api/migrations/firstofficer/plan?from=" + CoreExchange.encoded(oldHome))
                : core.post("/api/migrations/firstofficer", new JsonBody().with("from", oldHome).with("overwrite", overwrite));
    }
}
