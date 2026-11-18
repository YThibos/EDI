package io.github.ythibos.edi.domain.dispatch;

import java.util.Objects;
import java.util.Set;

import io.github.ythibos.edi.domain.branch.BranchKind;
import io.github.ythibos.edi.domain.task.TaskSize;

public record DispatchCategory(CategoryId id, Set<TaskSize> sizes, String when, Route route, String why, BranchKind branch, boolean researchFirst) {

    public DispatchCategory {
        Objects.requireNonNull(id, "A dispatch category has an id.");
        sizes = Set.copyOf(Objects.requireNonNullElse(sizes, Set.of()));
        Objects.requireNonNull(route, () -> "The category " + id + " names the model it runs on.");
        when = Objects.requireNonNullElse(when, "").strip();
        why = Objects.requireNonNullElse(why, "").strip();
        branch = Objects.requireNonNullElse(branch, BranchKind.FEATURE);
    }

    public boolean statesWhy() {
        return !why.isEmpty();
    }

    public boolean namesSizes() {
        return !sizes.isEmpty();
    }

    public boolean covers(TaskSize size) {
        return size != null && sizes.contains(size);
    }

    public boolean sharesASizeWith(DispatchCategory other) {
        return id.equals(other.id()) && sizes.stream().anyMatch(other.sizes()::contains);
    }

    public ChosenRoute chosenFor(TaskSize size) {
        return new ChosenRoute(route, "the category " + id + (namesSizes() ? " of size " + size.word() : ""));
    }

    public String name() {
        return "The category " + scope();
    }

    public String scope() {
        return id + (namesSizes() ? " of size " + TaskSize.wordsOf(sizes) : "");
    }
}
