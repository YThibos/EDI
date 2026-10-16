package io.github.ythibos.edi.testing;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class TestProcesses implements BeforeEachCallback, AfterEachCallback, AfterAllCallback {

    private static final Duration PATIENCE = Duration.ofSeconds(10);

    private final List<ProcessHandle> ofTheClass = new CopyOnWriteArrayList<>();
    private final List<ProcessHandle> ofTheTest = new CopyOnWriteArrayList<>();
    private volatile boolean inATest;

    public Process register(Process process) {
        register(process.toHandle());
        return process;
    }

    public void register(long pid) {
        ProcessHandle.of(pid).ifPresent(this::register);
    }

    public static boolean awaitExit(ProcessHandle process) {
        try {
            process.onExit().get(PATIENCE.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException | ExecutionException stillRunning) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        inATest = true;
    }

    @Override
    public void afterEach(ExtensionContext context) {
        inATest = false;
        stopWhatIsLeft(ofTheTest, context.getDisplayName());
    }

    @Override
    public void afterAll(ExtensionContext context) {
        stopWhatIsLeft(ofTheClass, context.getDisplayName());
    }

    private void register(ProcessHandle process) {
        (inATest ? ofTheTest : ofTheClass).add(process);
    }

    private static void stopWhatIsLeft(List<ProcessHandle> registered, String test) {
        List<ProcessHandle> leaked = registered.stream().flatMap(TestProcesses::treeOf).filter(ProcessHandle::isAlive).distinct().toList();
        registered.clear();
        leaked.forEach(ProcessHandle::destroyForcibly);
        leaked.forEach(TestProcesses::awaitExit);
        if (!leaked.isEmpty()) {
            throw new AssertionError(test + " left " + described(leaked) + " running; a test stops every process it starts.");
        }
    }

    private static Stream<ProcessHandle> treeOf(ProcessHandle process) {
        return Stream.concat(Stream.of(process), process.descendants());
    }

    private static String described(List<ProcessHandle> processes) {
        return processes.stream()
                .map(process -> process.pid() + " (" + process.info().command().orElse("unknown command") + ")")
                .collect(Collectors.joining(", ", "processes ", ""));
    }
}
