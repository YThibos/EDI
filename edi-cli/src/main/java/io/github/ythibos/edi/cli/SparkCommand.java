package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "spark",
        description = "Catches an idea as a spark, with its time, author, session and the task or project in focus, for the weekly gardening to sort;"
                + " lists the open sparks and sorts them.",
        subcommands = {SparkListCommand.class, SparkBacklogCommand.class, SparkFoldCommand.class, SparkDropCommand.class})
final class SparkCommand implements Callable<Integer> {

    private static final String STANDARD_INPUT = "-";

    @Spec
    private CommandSpec spec;

    @Mixin
    private CoreExchange core;

    @Parameters(arity = "0..1", paramLabel = "IDEA", description = "The idea in a few words, or - to read it from standard input.")
    private String idea;

    @Option(names = "--task", paramLabel = "TASK", description = "The task the idea is about, by its id or the D number of its decision;"
            + " without --task or --project, the task the primary or the captain acted on last.")
    private String task;

    @Option(names = "--project", paramLabel = "PROJECT", description = "The registered project the idea is about.")
    private String project;

    @Override
    public Integer call() {
        return core.post("/api/sparks", new JsonBody().with("text", ideaText()).with("task", task).with("project", project));
    }

    private String ideaText() {
        if (idea == null) {
            throw new ParameterException(spec.commandLine(), "Name the idea, or a subcommand; edi spark --help lists them.");
        }
        return idea.equals(STANDARD_INPUT) ? core.contentOf(STANDARD_INPUT) : idea;
    }
}
