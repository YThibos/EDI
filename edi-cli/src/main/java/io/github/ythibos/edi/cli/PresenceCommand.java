package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "presence", description = "Shows the captain's presence, or sets it: present, quiet, away with the captain's mandate, or a demo"
        + " that holds present.")
final class PresenceCommand implements Callable<Integer> {

    private static final String STANDARD_INPUT = "-";
    private static final String DEMO = "demo";
    private static final String OFF = "off";

    @Mixin
    private CoreExchange core;

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", arity = "0..1", paramLabel = "MODE", description = "present, quiet, away or demo; shows the presence when left out.")
    private String mode;

    @Parameters(index = "1", arity = "0..1", paramLabel = "off", description = "With demo: ends the demo.")
    private String off;

    @Option(names = "--mandate", paramLabel = "WORDS",
            description = "With away: the captain's words for their time away, verbatim; - reads them from standard input.")
    private String mandate;

    @Override
    public Integer call() {
        if (mode == null) {
            return core.get("/api/presence");
        }
        return core.post("/api/presence", new JsonBody().with("mode", chosenMode()).with("mandate", mandateWords()));
    }

    private String chosenMode() {
        if (off == null) {
            return mode;
        }
        if (!mode.equals(DEMO) || !off.equals(OFF)) {
            throw new ParameterException(spec.commandLine(), "Only a demo is ended with off: edi presence demo off.");
        }
        return "demo-off";
    }

    private String mandateWords() {
        return STANDARD_INPUT.equals(mandate) ? core.contentOf(STANDARD_INPUT) : mandate;
    }
}
