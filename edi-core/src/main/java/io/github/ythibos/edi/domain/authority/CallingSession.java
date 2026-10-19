package io.github.ythibos.edi.domain.authority;

import java.util.UUID;

public record CallingSession(UUID id) {

    public static final CallingSession UNNAMED = new CallingSession(null);

    public boolean namesItself() {
        return id != null;
    }
}
