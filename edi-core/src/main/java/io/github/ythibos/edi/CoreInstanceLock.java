package io.github.ythibos.edi;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.home.PrivateHome;
import io.github.ythibos.edi.adapter.process.ProcessTable;

@Component(CoreInstanceLock.BEAN)
@DependsOn(PrivateHome.BEAN)
public class CoreInstanceLock implements AutoCloseable {

    public static final String BEAN = "coreInstanceLock";

    private final FileChannel pidFile;
    private final FileLock lock;

    public CoreInstanceLock(EdiHome home, ProcessTable processes) {
        Path file = home.corePidFile();
        try {
            Files.createDirectories(file.getParent());
            this.pidFile = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            this.lock = pidFile.tryLock();
            if (lock == null) {
                pidFile.close();
                throw new IllegalStateException("Another EDI core runs on this home: it holds " + file + ".");
            }
            pidFile.truncate(0).write(ByteBuffer.wrap((processes.ownId() + "\n").getBytes(StandardCharsets.UTF_8)));
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    @Override
    public void close() throws IOException {
        lock.release();
        pidFile.close();
    }
}
