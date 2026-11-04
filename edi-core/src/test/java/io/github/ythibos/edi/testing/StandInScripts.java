package io.github.ythibos.edi.testing;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public final class StandInScripts {

    private static final String WRITE_AND_MARK_EXECUTABLE = "cat > \"$1\" && chmod 755 \"$1\"";

    private static final String COPY_KEEPING_MODE = "cp \"$1\" \"$2\"";

    private StandInScripts() {
    }

    public static Path copied(Path program, Path copy) throws IOException {
        Files.createDirectories(copy.getParent());
        Process copier = new ProcessBuilder("/bin/sh", "-c", COPY_KEEPING_MODE, "sh", program.toString(), copy.toString()).redirectErrorStream(true).start();
        awaitWritten(copier, copy);
        return copy;
    }

    public static Path installed(Path script, String content) throws IOException {
        Files.createDirectories(script.getParent());
        Process writer = new ProcessBuilder("/bin/sh", "-c", WRITE_AND_MARK_EXECUTABLE, "sh", script.toString()).redirectErrorStream(true).start();
        try (OutputStream input = writer.getOutputStream()) {
            input.write(content.getBytes(StandardCharsets.UTF_8));
        }
        awaitWritten(writer, script);
        return script;
    }

    private static void awaitWritten(Process writer, Path script) throws IOException {
        try {
            if (!writer.waitFor(Patience.FOR_REAL_WORK.toSeconds(), TimeUnit.SECONDS)) {
                writer.destroyForcibly();
                throw new IOException(script + " was not written within " + Patience.FOR_REAL_WORK.toSeconds() + " seconds.");
            }
        } catch (InterruptedException interrupted) {
            writer.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Writing " + script + " was interrupted.", interrupted);
        }
        if (writer.exitValue() != 0) {
            throw new IOException(script + " could not be written: " + new String(writer.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
