package io.github.ythibos.edi.adapter.fork;

import java.time.LocalDate;
import java.util.List;

public record ForkTags(
        String title,
        String kind,
        String repo,
        List<ForkDependency> dependencies,
        LocalDate since,
        LocalDate closed,
        Integer priority,
        ForkHold hold) {

    public ForkTags {
        dependencies = List.copyOf(dependencies);
    }
}
