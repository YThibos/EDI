package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

@Command(name = "said", description = "Records the captain's word in this session from a message they typed: on one task, or on matters that"
        + " concern no single task, such as touching a secret.")
final class CaptainSaidCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Mixin
    private CaptainsWordsCited words;

    @Spec
    private CommandSpec spec;

    @Option(names = "--task", paramLabel = "TASK", description = "The task the word is on: a task id, or the D number of its decision.")
    private String task;

    @Option(names = "--for", paramLabel = "ACT", description = "The one act this word is for: effort-above-high, which lets edi dispatch run this task"
            + " above high; a word without it never lifts effort.")
    private String act;

    @Override
    public Integer call() {
        if (act != null && task == null) {
            throw new ParameterException(spec.commandLine(), "A word for one act is on one task, so --for takes --task.");
        }
        return core.post("/api/captain/said", words.addedTo(new JsonBody(), core).with("task", task).with("for", act));
    }
}
