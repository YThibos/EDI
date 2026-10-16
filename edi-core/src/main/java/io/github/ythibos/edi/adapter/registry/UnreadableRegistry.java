package io.github.ythibos.edi.adapter.registry;

import java.nio.file.Path;

public class UnreadableRegistry extends RuntimeException {

    private static final long serialVersionUID = 1L;

    UnreadableRegistry(Path registry, Exception cause) {
        super("The project registry " + registry + " cannot be read: " + cause.getMessage(), cause);
    }
}
