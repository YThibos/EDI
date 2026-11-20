package io.github.ythibos.edi.web.access;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

final class OwnerOnlySecret {

    private static final int SECRET_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));

    private OwnerOnlySecret() {
    }

    static byte[] issuedInto(Path file) {
        byte[] random = new byte[SECRET_BYTES];
        RANDOM.nextBytes(random);
        byte[] secret = Base64.getUrlEncoder().withoutPadding().encode(random);
        writeTo(file, secret);
        return secret;
    }

    private static void writeTo(Path file, byte[] secret) {
        try {
            Files.createDirectories(file.getParent());
            Path written = Files.createTempFile(file.getParent(), file.getFileName() + "-", ".tmp", OWNER_ONLY);
            Files.write(written, secret);
            Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }
}
