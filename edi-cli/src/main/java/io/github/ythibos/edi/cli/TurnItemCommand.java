package io.github.ythibos.edi.cli;

import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command
abstract class TurnItemCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Option(names = "--project", paramLabel = "PROJECT", description = "A project the item is about; repeat it for each project.")
    private List<String> projects;

    @Parameters(arity = "1..*", paramLabel = "TEXT", description = "The item in the captain's words, on one line.")
    private List<String> words;

    @Override
    public Integer call() {
        return core.post(TurnCommand.TURN, new JsonBody().withJson(part(), "[" + item().text() + "]"));
    }

    abstract String part();

    private JsonBody item() {
        return new JsonBody().with("projects", projects).with("text", String.join(" ", words));
    }
}
