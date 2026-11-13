package io.github.ythibos.edi.domain.report;

import java.util.LinkedHashMap;
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

    public Turn followedBy(Turn later) {
        return new Turn(joined(done, later.done), joined(next, later.next), rewordedBy(later.tasks));
    }

    public String wording() {
        Stream<String> doneAndNext = Stream.concat(done.stream(), next.stream()).map(TurnItem::text);
        Stream<String> taskWordings = tasks.values().stream().flatMap(task -> Stream.of(task.text(), task.state()));
        return Stream.concat(doneAndNext, taskWordings).filter(Objects::nonNull).collect(Collectors.joining("\n"));
    }

    private static List<TurnItem> joined(List<TurnItem> earlier, List<TurnItem> later) {
        return Stream.concat(earlier.stream(), later.stream()).toList();
    }

    private Map<String, TaskWording> rewordedBy(Map<String, TaskWording> later) {
        Map<String, TaskWording> reworded = new LinkedHashMap<>(tasks);
        reworded.putAll(later);
        return reworded;
    }
}
