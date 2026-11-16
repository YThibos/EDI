package io.github.ythibos.edi.domain.report;

import java.util.List;

public record TurnItem(List<String> projects, String text) {

    public TurnItem {
        projects = List.copyOf(projects);
    }
}
