package io.github.ythibos.edi.application.port;

import java.time.Instant;

public interface Clock {

    Instant now();
}
