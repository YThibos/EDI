package io.github.ythibos.edi.web.report;

import java.util.List;
import java.util.Map;

public record Turn(List<TurnItem> done, List<TurnItem> next, Map<String, TaskWording> tasks) {

    public static final Turn NONE = new Turn(List.of(), List.of(), Map.of());

    public Turn {
        done = List.copyOf(done);
        next = List.copyOf(next);
        tasks = Map.copyOf(tasks);
    }
}
