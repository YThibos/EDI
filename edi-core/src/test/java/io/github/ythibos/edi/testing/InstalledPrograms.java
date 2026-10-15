package io.github.ythibos.edi.testing;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

public final class InstalledPrograms {

    private InstalledPrograms() {
    }

    public static boolean answers(String... versionCommand) {
        try {
            Process process = new ProcessBuilder(versionCommand).redirectErrorStream(true).start();
            process.getInputStream().transferTo(OutputStream.nullOutputStream());
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException missing) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
