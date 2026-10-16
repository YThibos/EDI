package io.github.ythibos.edi.adapter.fork;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class HoldReason {

    private static final String ENCODED = "fm-hold-v1:";

    private HoldReason() {
    }

    static String decoded(String stored) {
        if (!stored.startsWith(ENCODED)) {
            return stored;
        }
        String payload = stored.substring(ENCODED.length());
        try {
            byte[] reason = Base64.getDecoder().decode(payload);
            return Base64.getEncoder().encodeToString(reason).equals(payload) ? utf8(reason) : stored;
        } catch (IllegalArgumentException | CharacterCodingException undecodable) {
            return stored;
        }
    }

    private static String utf8(byte[] reason) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(reason))
                .toString();
    }
}
