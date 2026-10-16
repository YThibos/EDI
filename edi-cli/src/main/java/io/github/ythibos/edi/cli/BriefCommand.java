package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "brief", description = "Prints the brief a dispatch with these options would write, or with no category the brief already written.")
final class BriefCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "TASK", description = "A task id, or the D number of its decision.")
    private String task;

    @Mixin
    private DispatchCommand.DispatchOptions options;

    @Override
    public Integer call() {
        String brief = "/api/tasks/" + CoreExchange.encoded(task) + "/brief";
        return options.namesACategory() ? core.post(brief, options.body(core)) : core.get(brief);
    }
}
