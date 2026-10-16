package io.github.ythibos.edi.adapter.forge;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.Program;
import io.github.ythibos.edi.adapter.process.ProgramCall;
import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.adapter.process.ProgramRun;

@Component
public class ForgeCalls {

    private static final Path NEUTRAL_DIRECTORY = Path.of(System.getProperty("java.io.tmpdir"));
    private static final Set<String> INHERITED_TOKENS = Set.of("GH_TOKEN", "GITHUB_TOKEN", "GH_ENTERPRISE_TOKEN", "GITHUB_ENTERPRISE_TOKEN");

    private final ExternalPrograms programs;
    private final Map<Program, String> executables;

    public ForgeCalls(ExternalPrograms programs, @Value("${edi.forge.gh:}") String gh, @Value("${edi.forge.glab:}") String glab) {
        this.programs = programs;
        this.executables = Map.of(Program.GH, gh, Program.GLAB, glab);
    }

    String commandLineOf(ForgeCommand command) {
        String configured = executables.get(command.program());
        return "'" + (configured.isBlank() ? command.program().executable() : configured) + "' " + String.join(" ", command.words());
    }

    String run(ForgeCommand command, List<String> arguments, Map<String, String> environment, String input) {
        ProgramCall call = ProgramCall.of(command.program(), executables.get(command.program()), NEUTRAL_DIRECTORY,
                        Stream.concat(command.words().stream(), arguments.stream()).toList())
                .withholding(INHERITED_TOKENS)
                .withVariables(environment)
                .withInput(input);
        ProgramRun run = programs.run(call);
        if (!run.succeeded()) {
            throw new ProgramFailure(command.program().executable() + " " + String.join(" ", command.words()) + " failed: " + run.errors().strip());
        }
        return run.output();
    }
}
