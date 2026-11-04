package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "show", description = "Shows what the captain answered on a task's research boards, round by round, without delivering it to the worker.")
final class BoardShowCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "TASK", description = "A task id, or the D number of its decision.")
    private String task;

    @Option(names = "--round", paramLabel = "N", description = "Only round N.")
    private Integer round;

    @Override
    public Integer call() {
        return core.get("/api/tasks/" + CoreExchange.encoded(task) + "/board" + (round == null ? "" : "?round=" + round));
    }
}
