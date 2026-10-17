package io.github.ythibos.edi.adapter.process;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.primary.SessionProcess;

@Component
public class ProcessTable implements AutoCloseable {

    private static final Path PROCESSES = Path.of("/proc");

    private final ExecutorService escalations = Executors.newVirtualThreadPerTaskExecutor();

    public long ownId() {
        return ProcessHandle.current().pid();
    }

    public Optional<SessionProcess> identify(long pid) {
        return ProcessHandle.of(pid).map(process -> new SessionProcess(pid, process.info().startInstant().orElse(null)));
    }

    public boolean isRunning(SessionProcess process) {
        return ProcessHandle.of(process.pid())
                .filter(ProcessHandle::isAlive)
                .filter(running -> startsAt(running, process.startedAt()))
                .isPresent();
    }

    public OptionalLong parentOf(long pid) {
        return ProcessHandle.of(pid).flatMap(ProcessHandle::parent).map(parent -> OptionalLong.of(parent.pid())).orElseGet(OptionalLong::empty);
    }

    public List<Long> workingIn(Path directory) {
        Path inside = realPathOf(directory);
        List<Long> holders = new ArrayList<>();
        try (DirectoryStream<Path> processes = Files.newDirectoryStream(PROCESSES, "[0-9]*")) {
            processes.forEach(process -> workingDirectoryOf(process)
                    .filter(cwd -> cwd.startsWith(inside))
                    .ifPresent(_ -> holders.add(Long.parseLong(process.getFileName().toString()))));
        } catch (IOException unreadable) {
            throw new ProgramFailure("The process table cannot be read: " + unreadable.getMessage(), unreadable);
        }
        return holders;
    }

    public void stopTree(long pid, Duration grace) {
        ProcessHandle.of(pid).ifPresent(root -> {
            List<ProcessHandle> tree = Stream.concat(root.descendants(), Stream.of(root)).toList();
            tree.forEach(ProcessHandle::destroy);
            escalations.submit(() -> killWhatOutlives(tree, grace));
        });
    }

    @Override
    public void close() {
        escalations.shutdownNow();
    }

    private static boolean startsAt(ProcessHandle running, Instant recordedStart) {
        return recordedStart == null || running.info().startInstant().map(recordedStart::equals).orElse(true);
    }

    private static Path realPathOf(Path directory) {
        try {
            return directory.toRealPath();
        } catch (IOException absent) {
            return directory.toAbsolutePath().normalize();
        }
    }

    private static Optional<Path> workingDirectoryOf(Path process) {
        try {
            return Optional.of(Files.readSymbolicLink(process.resolve("cwd")));
        } catch (IOException | SecurityException | UnsupportedOperationException unreadable) {
            return Optional.empty();
        }
    }

    private static void killWhatOutlives(List<ProcessHandle> tree, Duration grace) {
        CompletableFuture<?>[] exits = tree.stream().map(ProcessHandle::onExit).toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(exits).get(grace.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException outlived) {
            tree.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        }
    }
}
