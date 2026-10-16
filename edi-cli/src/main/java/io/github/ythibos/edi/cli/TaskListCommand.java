package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(name = "list", description = "Lists the tasks in flight and queued, or the tasks in one state.")
final class TaskListCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Option(names = "--state", paramLabel = "STATE", description = "queued, in-flight, done or dropped.")
    private String state;

    @Override
    public Integer call() {
        return core.get(state == null ? "/api/tasks" : "/api/tasks?state=" + CoreExchange.encoded(state));
    }
}
