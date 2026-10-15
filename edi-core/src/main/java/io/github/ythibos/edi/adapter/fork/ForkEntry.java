package io.github.ythibos.edi.adapter.fork;

import java.time.Instant;
import java.util.List;

public record ForkEntry(int line, ForkSection section, String id, ForkTags tags, List<String> body, ForkDecision decision, Instant holdSet) {

    public ForkEntry {
        body = List.copyOf(body);
    }
}
