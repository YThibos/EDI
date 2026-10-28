package io.github.ythibos.edi.adapter.runtime;

import java.nio.file.Path;
import java.util.List;

import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.Program;
import io.github.ythibos.edi.adapter.process.ProgramCall;
import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.adapter.process.ProgramRun;

final class MiseTrust {

    private static final List<String> SHOW_TRUST = List.of("trust", "--show");
    private static final String TRUSTED = ": trusted";

    private final ExternalPrograms programs;
    private final String mise;

    MiseTrust(ExternalPrograms programs, String mise) {
        this.programs = programs;
        this.mise = mise;
    }

    boolean trusts(Path clone) {
        try {
            return grantsTrust(programs.run(ProgramCall.of(Program.MISE, mise, clone, SHOW_TRUST)));
        } catch (ProgramFailure unanswered) {
            return false;
        }
    }

    private static boolean grantsTrust(ProgramRun shown) {
        List<String> configurations = shown.output().lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        return shown.succeeded() && !configurations.isEmpty() && configurations.stream().allMatch(line -> line.endsWith(TRUSTED));
    }
}
