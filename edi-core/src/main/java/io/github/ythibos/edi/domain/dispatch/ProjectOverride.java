package io.github.ythibos.edi.domain.dispatch;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.ythibos.edi.domain.project.ProjectName;

public record ProjectOverride(ProjectName project, Set<CategoryId> categories, Route route, String why) {

    public ProjectOverride {
        Objects.requireNonNull(project, "A project override names its project.");
        categories = Set.copyOf(Objects.requireNonNullElse(categories, Set.of()));
        Objects.requireNonNull(route, () -> "The override for " + project + " names the model it runs on.");
        why = Objects.requireNonNullElse(why, "").strip();
    }

    public boolean statesWhy() {
        return !why.isEmpty();
    }

    public boolean covers(List<ProjectName> projects, CategoryId category) {
        return projects.contains(project) && coversCategory(category);
    }

    private boolean coversCategory(CategoryId category) {
        return coversEveryCategory() || categories.contains(category);
    }

    private boolean coversEveryCategory() {
        return categories.isEmpty();
    }
}
