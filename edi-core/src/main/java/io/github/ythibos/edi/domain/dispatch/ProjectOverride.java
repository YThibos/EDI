package io.github.ythibos.edi.domain.dispatch;

import java.util.List;
import java.util.Objects;

import io.github.ythibos.edi.domain.project.ProjectName;

public record ProjectOverride(ProjectName project, Route route, String why) {

    public ProjectOverride {
        Objects.requireNonNull(project, "A project override names its project.");
        Objects.requireNonNull(route, () -> "The override for " + project + " names the model it runs on.");
        why = Objects.requireNonNullElse(why, "").strip();
    }

    public boolean statesWhy() {
        return !why.isEmpty();
    }

    public boolean covers(List<ProjectName> projects) {
        return projects.contains(project);
    }
}
