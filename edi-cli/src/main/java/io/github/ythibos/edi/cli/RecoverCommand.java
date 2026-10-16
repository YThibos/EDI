package io.github.ythibos.edi.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "recover", description = "Puts dead workers on the outage hold list and shows it numbered, or acts on one number the captain named:"
        + " edi recover 2 resume, edi recover 4 fresh --effort medium.")
final class RecoverCommand implements Callable<Integer> {

    @Mixin
    private CoreExchange core;

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", arity = "0..1", paramLabel = "NUMBER", description = "The number on the outage hold list the captain named.")
    private Integer number;

    @Parameters(index = "1", arity = "0..1", paramLabel = "ACTION", description = "resume, which resumes the recorded session, or fresh, which starts a new one.")
    private String action;

    @Option(names = "--model", paramLabel = "MODEL", description = "The model the captain named for this relaunch.")
    private String model;

    @Option(names = "--effort", paramLabel = "EFFORT", description = "The effort the captain named for this relaunch: low, medium or high.")
    private String effort;

    @Override
    public Integer call() {
        if (number == null) {
            return core.post("/api/recovery", "{}");
        }
        if (action == null) {
            throw new ParameterException(spec.commandLine(), "Name resume or fresh after number " + number + ", as the captain did.");
        }
        return core.post("/api/recovery/" + number + "/" + CoreExchange.encoded(action), new JsonBody().with("model", model).with("effort", effort));
    }
}
