package io.github.ythibos.edi.adapter.process;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

public final class OwnWorkingDirectory {

    private static final int CHANGED = 0;

    private OwnWorkingDirectory() {
    }

    public static void moveInto(Path directory) {
        if (changeDirectory(directory) != CHANGED) {
            throw new ProgramFailure("EDI core cannot move into " + directory + ", so it does not start.");
        }
    }

    @SuppressWarnings("java:S1181") // invokeExact declares Throwable, while chdir itself reports a failure through its result
    private static int changeDirectory(Path directory) {
        try (Arena arena = Arena.ofConfined()) {
            return (int) chdir().invokeExact(arena.allocateFrom(directory.toString()));
        } catch (Throwable unreachable) {
            throw new ProgramFailure("EDI core cannot call chdir to move into " + directory + ": " + unreachable.getMessage(), unreachable);
        }
    }

    @SuppressWarnings("restricted") // chdir takes one NUL-terminated path, which the confined arena allocates and keeps alive for the call
    private static MethodHandle chdir() {
        Linker linker = Linker.nativeLinker();
        return linker.downcallHandle(linker.defaultLookup().findOrThrow("chdir"), FunctionDescriptor.of(JAVA_INT, ADDRESS));
    }
}
