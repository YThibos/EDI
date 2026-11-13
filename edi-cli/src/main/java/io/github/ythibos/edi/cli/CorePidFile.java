package io.github.ythibos.edi.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

record CorePidFile(Path file) {

    private static final Path PROCESSES = Path.of("/proc");

    static CorePidFile in(Path home) {
        return new CorePidFile(home.resolve("state").resolve("core.pid"));
    }

    boolean isHeld() {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            return channel.tryLock() == null;
        } catch (NoSuchFileException absent) {
            return false;
        } catch (IOException unopenable) {
            throw new UncheckedIOException(unopenable);
        }
    }

    String recordedProcess() {
        try {
            return Files.readString(file).strip();
        } catch (NoSuchFileException absent) {
            return "";
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    Optional<Path> workingDirectoryOfItsProcess() {
        String process = recordedProcess();
        if (process.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readSymbolicLink(PROCESSES.resolve(process).resolve("cwd")));
        } catch (IOException gone) {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return file.toString();
    }
}
