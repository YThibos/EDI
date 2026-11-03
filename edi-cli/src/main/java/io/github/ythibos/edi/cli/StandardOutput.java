package io.github.ythibos.edi.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

record StandardOutput(Path descriptor) {

    static final StandardOutput OF_THIS_PROCESS = new StandardOutput(Path.of("/proc/self/fd/1"));

    private static final Path NOWHERE = Path.of("/dev/null");
    private static final String INTO_THE_VOID = "its standard output is /dev/null";
    private static final String CLOSED = "its standard output is closed";
    private static final String FLAGS = "flags:";
    private static final int ACCESS_MODE = 3;
    private static final int READ_ONLY = 0;

    Optional<String> whyNobodyReadsIt() {
        if (!Files.isDirectory(descriptor.getParent())) {
            return Optional.empty();
        }
        return whereItLeads().or(this::whetherItIsOpenOnlyForReading);
    }

    private Optional<String> whereItLeads() {
        try {
            return Files.readSymbolicLink(descriptor).equals(NOWHERE) ? Optional.of(INTO_THE_VOID) : Optional.empty();
        } catch (NoSuchFileException closed) {
            return Optional.of(CLOSED);
        } catch (IOException | UnsupportedOperationException unreadable) {
            return Optional.empty();
        }
    }

    private Optional<String> whetherItIsOpenOnlyForReading() {
        try (Stream<String> lines = Files.lines(descriptorInfo())) {
            return lines.filter(line -> line.startsWith(FLAGS))
                    .map(StandardOutput::accessMode)
                    .filter(mode -> mode == READ_ONLY)
                    .findFirst()
                    .map(readOnly -> CLOSED);
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    private Path descriptorInfo() {
        return descriptor.getParent().resolveSibling("fdinfo").resolve(descriptor.getFileName());
    }

    private static int accessMode(String flagsLine) {
        return Integer.parseInt(flagsLine.substring(FLAGS.length()).strip(), 8) & ACCESS_MODE;
    }
}
