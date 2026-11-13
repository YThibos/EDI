package io.github.ythibos.edi.domain.report;

import java.util.List;

public record TaskWording(String text, String state, List<String> projects) {

    public static final TaskWording NONE = new TaskWording(null, null, null);

    public TaskWording {
        projects = projects == null ? null : List.copyOf(projects);
    }
}
