package io.github.ythibos.edi.application.port;

import java.time.Instant;
import java.time.ZoneId;

public interface Clock {

    Instant now();

    ZoneId zone();
}
