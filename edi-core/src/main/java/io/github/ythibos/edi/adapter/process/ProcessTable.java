package io.github.ythibos.edi.adapter.process;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
import io.github.ythibos.edi.domain.worker.WorkerWindow;

@Component
public class ProcessTable implements AutoCloseable {

    private static final Path PROCESSES = Path.of("/proc");
    private static final int START_TICK_AFTER_THE_NAME = 19;

    private final ExecutorService escalations = Executors.newVirtualThreadPerTaskExecutor();

    public long ownId() {
        return ProcessHandle.current().pid();
    }

    public Optional<SessionProcess> identify(long pid) {
        return ProcessHandle.of(pid).map(_ -> new SessionProcess(pid, startTickOf(pid).orElse(null)));
    }

    public boolean isRunning(SessionProcess process) {
        return ProcessHandle.of(process.pid())
                .filter(ProcessHandle::isAlive)
                .filter(_ -> startsAs(process))
                .isPresent();
    }

    public OptionalLong parentOf(long pid) {
        return ProcessHandle.of(pid).flatMap(ProcessHandle::parent).map(parent -> OptionalLong.of(parent.pid())).orElseGet(OptionalLong::empty);
    }

    public boolean isStillThePaneOf(WorkerWindow window) {
        OptionalLong parent = parentOf(window.panePid());
        return parent.isPresent() && parent.getAsLong() == window.serverPid();
    }

    public List<Long> workingIn(Path directory) {
        Path inside = realPathOf(directory);
        List<Long> holders = new ArrayList<>();
        try (DirectoryStream<Path> processes = Files.newDirectoryStream(PROCESSES, "[0-9]*")) {
            processes.forEach(process -> {
                if (linkIn(process, "cwd", inside)) {
                    holders.add(Long.parseLong(process.getFileName().toString()));
                }
            });
        } catch (IOException unreadable) {
            throw new ProgramFailure("The process table cannot be read: " + unreadable.getMessage(), unreadable);
        }
        return holders;
    }

    public List<Long> heldIn(Path directory) {
        Path inside = realPathOf(directory);
        List<Long> holders = new ArrayList<>();
        try (DirectoryStream<Path> processes = Files.newDirectoryStream(PROCESSES, "[0-9]*")) {
            processes.forEach(process -> {
                if (linkIn(process, "cwd", inside) || linkIn(process, "exe", inside)) {
                    holders.add(Long.parseLong(process.getFileName().toString()));
                }
            });
        } catch (IOException unreadable) {
            throw new ProgramFailure("The process table cannot be read: " + unreadable.getMessage(), unreadable);
        }
        return holders;
    }

    public List<Long> treeOf(long pid) {
        return ProcessHandle.of(pid)
                .map(root -> Stream.concat(Stream.of(root), root.descendants()).map(ProcessHandle::pid).toList())
                .orElseGet(List::of);
    }

    public List<Long> ancestryOf(long pid) {
        List<Long> ancestry = new ArrayList<>();
        Optional<ProcessHandle> process = ProcessHandle.of(pid);
        while (process.isPresent()) {
            ancestry.add(process.get().pid());
            process = process.get().parent();
        }
        return ancestry;
    }

    public void stopEach(List<Long> pids, Duration grace) {
        List<ProcessHandle> stopped = pids.stream().map(ProcessHandle::of).flatMap(Optional::stream).toList();
        stopped.forEach(ProcessHandle::destroy);
        escalations.submit(() -> killWhatOutlives(stopped, grace));
    }

    public List<List<String>> argumentsInTree(long pid) {
        return ProcessHandle.of(pid)
                .map(root -> Stream.concat(Stream.of(root), root.descendants()).map(ProcessTable::argumentsOf).toList())
                .orElseGet(List::of);
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

    private static Optional<Long> startTickOf(long pid) {
        try {
            return Optional.of(startTickIn(Files.readString(PROCESSES.resolve(Long.toString(pid)).resolve("stat"))));
        } catch (IOException gone) {
            return Optional.empty();
        }
    }

    private static long startTickIn(String stat) {
        String afterTheName = stat.substring(stat.lastIndexOf(')') + 2);
        return Long.parseLong(afterTheName.split(" ")[START_TICK_AFTER_THE_NAME]);
    }

    private static boolean startsAs(SessionProcess recorded) {
        return startTickOf(recorded.pid()).map(recorded::isTheOneStartedAt).orElse(true);
    }

    private static Path realPathOf(Path directory) {
        try {
            return directory.toRealPath();
        } catch (IOException absent) {
            return directory.toAbsolutePath().normalize();
        }
    }

    private static boolean linkIn(Path process, String link, Path inside) {
        return linkOf(process, link).filter(target -> target.startsWith(inside)).isPresent();
    }

    private static Optional<Path> linkOf(Path process, String link) {
        try {
            return Optional.of(Files.readSymbolicLink(process.resolve(link)));
        } catch (IOException | SecurityException | UnsupportedOperationException unreadable) {
            return Optional.empty();
        }
    }

    private static List<String> argumentsOf(ProcessHandle process) {
        return process.info().arguments().map(List::of).orElseGet(List::of);
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
