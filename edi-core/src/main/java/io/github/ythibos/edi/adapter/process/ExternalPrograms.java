package io.github.ythibos.edi.adapter.process;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

@Component
public class ExternalPrograms {

    private static final Duration PATIENCE = Duration.ofMinutes(2);

    public ProgramRun run(Program program, Path directory, List<String> arguments) {
        return run(ProgramCall.of(program, null, directory, arguments));
    }

    public ProgramRun run(ProgramCall call) {
        return captured(call, process -> awaitCompletion(call.program(), process, PATIENCE));
    }

    public ProgramRun runUntilItEnds(ProgramCall call) {
        return captured(call, process -> awaitTheEnd(call.program(), process));
    }

    public int runInto(Path log, Program program, Path directory, List<String> arguments, Duration patience) {
        try {
            Files.createDirectories(log.getParent());
            Process process = start(ProgramCall.of(program, null, directory, arguments), log, log);
            awaitCompletion(program, process, patience);
            return process.exitValue();
        } catch (IOException unstartable) {
            throw new ProgramFailure(program.executable() + " could not run: " + unstartable.getMessage(), unstartable);
        }
    }

    private static ProgramRun captured(ProgramCall call, Consumer<Process> awaiting) {
        Program program = call.program();
        try (Capture output = Capture.create(); Capture errors = Capture.create()) {
            Process process = start(call, output.file(), errors.file());
            awaiting.accept(process);
            return new ProgramRun(program, process.exitValue(), output.read(), errors.read());
        } catch (IOException unstartable) {
            throw new ProgramFailure(program.executable() + " could not run: " + unstartable.getMessage(), unstartable);
        }
    }

    private static Process start(ProgramCall call, Path output, Path errors) throws IOException {
        ProcessBuilder launch = new ProcessBuilder(Stream.concat(Stream.of(call.executable()), call.arguments().stream()).toList())
                .directory(call.directory().toFile());
        launch.environment().keySet().removeAll(call.withheldVariables());
        launch.environment().putAll(call.environment());
        if (output.equals(errors)) {
            launch.redirectOutput(output.toFile()).redirectErrorStream(true);
        } else {
            launch.redirectOutput(output.toFile()).redirectError(errors.toFile());
        }
        Process process = launch.start();
        try (OutputStream input = process.getOutputStream()) {
            if (call.input() != null) {
                input.write(call.input().getBytes(StandardCharsets.UTF_8));
            }
        }
        return process;
    }

    private static void awaitCompletion(Program program, Process process, Duration patience) {
        try {
            if (!process.waitFor(patience.toSeconds(), TimeUnit.SECONDS)) {
                stop(process);
                throw new ProgramFailure(program.executable() + " did not finish within " + patience.toMinutes() + " minutes.");
            }
        } catch (InterruptedException interrupted) {
            stop(process);
            Thread.currentThread().interrupt();
            throw new ProgramFailure(program.executable() + " was interrupted.", interrupted);
        }
    }

    private static void awaitTheEnd(Program program, Process process) {
        try {
            process.waitFor();
        } catch (InterruptedException interrupted) {
            stop(process);
            Thread.currentThread().interrupt();
            throw new ProgramFailure(program.executable() + " was interrupted.", interrupted);
        }
    }

    private static void stop(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private record Capture(Path file) implements AutoCloseable {

        private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY =
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));

        static Capture create() throws IOException {
            return new Capture(Files.createTempFile("edi-program-", ".txt", OWNER_ONLY));
        }

        String read() throws IOException {
            return Files.readString(file);
        }

        @Override
        public void close() {
            try {
                Files.deleteIfExists(file);
            } catch (IOException undeletable) {
                throw new UncheckedIOException(undeletable);
            }
        }
    }
}
