package io.github.ythibos.edi.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

record CoreConnection(CoreAddress address, String token) {

    static CoreConnection in(Path home, Path tokenFile) {
        Path portFile = CoreAddress.portFileIn(home);
        CoreAddress address = CoreAddress.recordedIn(portFile)
                .orElseThrow(() -> new CoreUnavailable("EDI core is not running: " + portFile + " records no port."));
        return new CoreConnection(address, tokenIn(tokenFile));
    }

    private static String tokenIn(Path tokenFile) {
        try {
            String token = Files.readString(tokenFile).strip();
            if (token.isEmpty()) {
                throw new CoreUnavailable("EDI core is not running: " + tokenFile + " holds no token.");
            }
            return token;
        } catch (NoSuchFileException absent) {
            throw new CoreUnavailable("EDI core is not running: " + tokenFile + " holds no token.");
        } catch (IOException unreadable) {
            throw new CoreUnavailable("EDI core cannot be reached: " + tokenFile + " cannot be read.");
        }
    }
}
