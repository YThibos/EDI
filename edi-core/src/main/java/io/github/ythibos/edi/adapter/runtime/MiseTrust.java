package io.github.ythibos.edi.adapter.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.Program;
import io.github.ythibos.edi.adapter.process.ProgramCall;
import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.adapter.process.ProgramRun;

final class MiseTrust {

    private static final List<String> SHOW_TRUST = List.of("trust", "--show");
    private static final String VERDICT_SEPARATOR = ": ";
    private static final String TRUSTED = "trusted";
    private static final String HOME = "~";

    private final ExternalPrograms programs;
    private final String mise;
    private final Path home;

    MiseTrust(ExternalPrograms programs, String mise, Path home) {
        this.programs = programs;
        this.mise = mise;
        this.home = home;
    }

    boolean trusts(Path clone) {
        try {
            return grantsTrust(programs.run(ProgramCall.of(Program.MISE, mise, clone, SHOW_TRUST)), clone.toAbsolutePath().normalize());
        } catch (ProgramFailure unanswered) {
            return false;
        }
    }

    private boolean grantsTrust(ProgramRun shown, Path clone) {
        List<ShownTrust> ownConfigurations = shown.output().lines().map(this::shownTrust).flatMap(Optional::stream)
                .filter(configuration -> configuration.directory().startsWith(clone)).toList();
        return shown.succeeded() && !ownConfigurations.isEmpty() && ownConfigurations.stream().allMatch(ShownTrust::trusted);
    }

    private Optional<ShownTrust> shownTrust(String line) {
        int separator = line.lastIndexOf(VERDICT_SEPARATOR);
        if (separator < 0) {
            return Optional.empty();
        }
        String verdict = line.substring(separator + VERDICT_SEPARATOR.length()).strip();
        return Optional.of(new ShownTrust(directoryOf(line.substring(0, separator).strip()), verdict.equals(TRUSTED)));
    }

    private Path directoryOf(String shown) {
        boolean underHome = shown.equals(HOME) || shown.startsWith(HOME + "/");
        return (underHome ? Path.of(home + shown.substring(HOME.length())) : Path.of(shown)).normalize();
    }

    private record ShownTrust(Path directory, boolean trusted) {
    }
}
