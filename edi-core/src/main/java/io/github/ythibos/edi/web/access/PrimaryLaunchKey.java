package io.github.ythibos.edi.web.access;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;

public final class PrimaryLaunchKey {

    public static final String HEADER = "EDI-Primary-Launch-Key";

    private final byte[] value;

    private PrimaryLaunchKey(byte[] value) {
        this.value = value;
    }

    public static PrimaryLaunchKey keptIn(Path keyFile) {
        byte[] kept = keptValueIn(keyFile);
        return new PrimaryLaunchKey(kept.length == 0 ? OwnerOnlySecret.issuedInto(keyFile) : kept);
    }

    public boolean admits(String presented) {
        return presented != null && MessageDigest.isEqual(value, presented.strip().getBytes(StandardCharsets.US_ASCII));
    }

    public String text() {
        return new String(value, StandardCharsets.US_ASCII);
    }

    private static byte[] keptValueIn(Path keyFile) {
        try {
            return Files.readString(keyFile, StandardCharsets.US_ASCII).strip().getBytes(StandardCharsets.US_ASCII);
        } catch (NoSuchFileException absent) {
            return new byte[0];
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
