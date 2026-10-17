package io.github.ythibos.edi.web.report;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record Turn(List<TurnItem> done, List<TurnItem> next, Map<String, TaskWording> tasks) {

    public static final Turn NONE = new Turn(List.of(), List.of(), Map.of());

    public Turn {
        done = List.copyOf(done);
        next = List.copyOf(next);
        tasks = Map.copyOf(tasks);
    }

    public String wording() {
        Stream<String> doneAndNext = Stream.concat(done.stream(), next.stream()).map(TurnItem::text);
        Stream<String> taskWordings = tasks.values().stream().flatMap(task -> Stream.of(task.text(), task.state()));
        return Stream.concat(doneAndNext, taskWordings).filter(Objects::nonNull).collect(Collectors.joining("\n"));
    }
}
