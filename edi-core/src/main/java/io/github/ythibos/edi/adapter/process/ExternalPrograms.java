package io.github.ythibos.edi.adapter.process;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

@Component
public class ExternalPrograms {

    private static final Duration PATIENCE = Duration.ofMinutes(2);

    public ProgramRun run(Program program, Path directory, List<String> arguments) {
        try (Capture output = Capture.create(); Capture errors = Capture.create()) {
            Process process = start(program, directory, arguments, output, errors);
            awaitCompletion(program, process);
            return new ProgramRun(program, process.exitValue(), output.read(), errors.read());
        } catch (IOException unstartable) {
            throw new ProgramFailure(program.executable() + " could not run: " + unstartable.getMessage(), unstartable);
        }
    }

    private static Process start(Program program, Path directory, List<String> arguments, Capture output, Capture errors)
            throws IOException {
        Process process = new ProcessBuilder(Stream.concat(Stream.of(program.executable()), arguments.stream()).toList())
                .directory(directory.toFile())
                .redirectOutput(output.file().toFile())
                .redirectError(errors.file().toFile())
                .start();
        process.getOutputStream().close();
        return process;
    }

    private static void awaitCompletion(Program program, Process process) {
        try {
            if (!process.waitFor(PATIENCE.toSeconds(), TimeUnit.SECONDS)) {
                stop(process);
                throw new ProgramFailure(program.executable() + " did not finish within " + PATIENCE.toMinutes() + " minutes.");
            }
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

        static Capture create() throws IOException {
            return new Capture(Files.createTempFile("edi-program-", ".txt"));
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
