package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "relaunch", description = "Relaunches a task's dead or stopped worker in its own copy, resuming its recorded session on the Claude"
        + " login it ran on, or starting afresh with a handover on its project's login when its context is full or the captain asks for it.")
final class WorkerRelaunchCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Parameters(paramLabel = "TASK", description = "A task id, or the D number of its decision.")
    private String task;

    @Option(names = "--fresh", description = "Starts a new session with a handover instead of resuming the recorded one, on the captain's word.")
    private boolean fresh;

    @Override
    public Integer call() {
        return core.post("/api/tasks/" + CoreExchange.encoded(task) + "/relaunch", new JsonBody().with("fresh", fresh));
    }
}
