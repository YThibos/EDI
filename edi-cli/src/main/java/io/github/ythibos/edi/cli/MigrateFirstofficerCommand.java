package io.github.ythibos.edi.cli;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(name = "firstofficer", description = "Plans the import of a firstofficer home's backlog, holds and decisions, and changes nothing.")
final class MigrateFirstofficerCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Option(names = "--from", required = true, paramLabel = "HOME", description = "The firstofficer home to read.")
    private Path from;

    @Option(names = "--dry-run", required = true, description = "Print the plan and its counts without importing anything.")
    private boolean dryRun;

    @Override
    public Integer call() {
        return core.get("/api/migrations/firstofficer/plan?from=" + CoreExchange.encoded(from.toAbsolutePath().normalize().toString()));
    }
}
