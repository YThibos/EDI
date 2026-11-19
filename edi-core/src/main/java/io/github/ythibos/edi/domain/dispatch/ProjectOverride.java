package io.github.ythibos.edi.domain.dispatch;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.TaskSize;

public record ProjectOverride(ProjectName project, Set<CategoryId> categories, Set<TaskSize> sizes, Route route, String why) {

    public ProjectOverride {
        Objects.requireNonNull(project, "A project override names its project.");
        categories = Set.copyOf(Objects.requireNonNullElse(categories, Set.of()));
        sizes = Set.copyOf(Objects.requireNonNullElse(sizes, Set.of()));
        Objects.requireNonNull(route, () -> "The override for " + project + " names the model it runs on.");
        why = Objects.requireNonNullElse(why, "").strip();
    }

    public boolean statesWhy() {
        return !why.isEmpty();
    }

    public boolean covers(List<ProjectName> projects, CategoryId category, TaskSize size) {
        return projects.contains(project) && coversCategory(category) && coversSize(size);
    }

    public boolean leavesNothingFor(ProjectOverride later) {
        return project.equals(later.project()) && coversEveryCategoryOf(later) && coversEverySizeOf(later);
    }

    public ChosenRoute chosenFor(CategoryId category, TaskSize size) {
        return ChosenRoute.chosenBy(route, "the override for " + project + " on " + category + matchedSize(size));
    }

    public String name() {
        return "The override for " + scope();
    }

    public String scope() {
        String onCategories = coversEveryCategory() ? "" : " on " + categories.stream().map(CategoryId::value).sorted().toList();
        return project + onCategories + (namesSizes() ? " of size " + TaskSize.wordsOf(sizes) : "");
    }

    private boolean coversCategory(CategoryId category) {
        return coversEveryCategory() || categories.contains(category);
    }

    private boolean coversEveryCategory() {
        return categories.isEmpty();
    }

    private boolean coversSize(TaskSize size) {
        return !namesSizes() || size != null && sizes.contains(size);
    }

    private boolean namesSizes() {
        return !sizes.isEmpty();
    }

    private boolean coversEveryCategoryOf(ProjectOverride later) {
        return coversEveryCategory() || !later.coversEveryCategory() && categories.containsAll(later.categories());
    }

    private boolean coversEverySizeOf(ProjectOverride later) {
        return !namesSizes() || later.namesSizes() && sizes.containsAll(later.sizes());
    }

    private String matchedSize(TaskSize size) {
        return namesSizes() ? " of size " + size.word() : "";
    }
}
