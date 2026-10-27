package io.github.ythibos.edi.testing;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class InstalledPrograms {

    private static final File WHERE_FIXTURES_LIVE = new File(System.getProperty("java.io.tmpdir"));
    private static final Map<List<String>, Boolean> ANSWERED = new ConcurrentHashMap<>();

    private InstalledPrograms() {
    }

    public static boolean answers(String... versionCommand) {
        return ANSWERED.computeIfAbsent(List.of(versionCommand), InstalledPrograms::asked);
    }

    private static boolean asked(List<String> versionCommand) {
        try {
            Process process = new ProcessBuilder(versionCommand).directory(WHERE_FIXTURES_LIVE).redirectErrorStream(true).start();
            process.getInputStream().transferTo(OutputStream.nullOutputStream());
            return process.waitFor(Patience.FOR_REAL_WORK.toSeconds(), TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException missing) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while asking " + versionCommand.getFirst() + " for its version.", interrupted);
        }
    }
}
