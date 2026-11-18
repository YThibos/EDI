package io.github.ythibos.edi.web.access;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;

public final class ApiToken {

    private final byte[] value;

    private ApiToken(byte[] value) {
        this.value = value;
    }

    public static ApiToken issuedInto(Path tokenFile) {
        return new ApiToken(OwnerOnlySecret.issuedInto(tokenFile));
    }

    public boolean admits(String presented) {
        return presented != null && MessageDigest.isEqual(value, presented.getBytes(StandardCharsets.US_ASCII));
    }
}
