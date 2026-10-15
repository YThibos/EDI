package io.github.ythibos.edi.web.access;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

public final class ApiToken {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));

    private final byte[] value;

    private ApiToken(byte[] value) {
        this.value = value;
    }

    public static ApiToken issuedInto(Path tokenFile) {
        byte[] random = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(random);
        ApiToken token = new ApiToken(Base64.getUrlEncoder().withoutPadding().encode(random));
        token.writeTo(tokenFile);
        return token;
    }

    public boolean admits(String presented) {
        return presented != null && MessageDigest.isEqual(value, presented.getBytes(StandardCharsets.US_ASCII));
    }

    private void writeTo(Path tokenFile) {
        try {
            Files.createDirectories(tokenFile.getParent());
            Path written = Files.createTempFile(tokenFile.getParent(), "core.token-", ".tmp", OWNER_ONLY);
            Files.write(written, value);
            Files.move(written, tokenFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }
}
