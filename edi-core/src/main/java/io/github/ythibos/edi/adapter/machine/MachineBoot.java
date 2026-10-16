package io.github.ythibos.edi.adapter.machine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.worker.BootId;

@Component
public class MachineBoot {

    private final BootId current;

    public MachineBoot(@Value("${edi.machine.boot-id}") Path bootIdFile) {
        this.current = readFrom(bootIdFile);
    }

    public BootId current() {
        return current;
    }

    private static BootId readFrom(Path bootIdFile) {
        try {
            return new BootId(Files.readString(bootIdFile));
        } catch (IOException unreadable) {
            throw new UncheckedIOException("EDI tells a machine restart by its boot id, but " + bootIdFile + " cannot be read.", unreadable);
        }
    }
}
