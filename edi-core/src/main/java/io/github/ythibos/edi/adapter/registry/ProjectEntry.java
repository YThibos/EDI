package io.github.ythibos.edi.adapter.registry;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.branch.BranchKind;
import io.github.ythibos.edi.domain.project.AiScaffolding;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.project.Project;
import io.github.ythibos.edi.domain.project.ShipBriefRule;
import io.github.ythibos.edi.domain.task.BaseBranch;

public record ProjectEntry(
        Project project,
        Path projectClone,
        Path captainCheckout,
        BaseBranch base,
        DeliveryMode delivery,
        Set<BranchKind> branchKinds,
        boolean craftRules,
        AiScaffolding aiScaffolding,
        Integer capacity,
        String verify,
        boolean largeBuilds,
        List<ShipBriefRule> shipBriefRules) {

    public ProjectEntry {
        Objects.requireNonNull(project, "A registry entry is for a project.");
        Objects.requireNonNull(projectClone, () -> project.name() + " has a clone.");
        branchKinds = Set.copyOf(branchKinds);
        shipBriefRules = List.copyOf(shipBriefRules);
        if (capacity != null && capacity < 1) {
            throw new IllegalArgumentException(project.name() + " runs at least one worker at a time when it names a capacity: " + capacity);
        }
    }

    public BaseBranch requireBase() {
        if (base == null) {
            throw new Refusal(project.name() + " names no base branch in the project registry; add base: to its entry.");
        }
        return base;
    }

    public OptionalInt workerCapacity() {
        return capacity == null ? OptionalInt.empty() : OptionalInt.of(capacity);
    }
}
