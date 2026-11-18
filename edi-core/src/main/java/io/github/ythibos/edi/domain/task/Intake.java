package io.github.ythibos.edi.domain.task;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;

public record Intake(
        TaskKind kind,
        String title,
        List<ProjectName> projects,
        WorkKey workKey,
        BaseBranch base,
        List<TaskId> blockedBy,
        DeliveryMode delivery,
        TaskSize size) {

    public Intake {
        title = title.strip();
        projects = List.copyOf(projects);
        blockedBy = List.copyOf(blockedBy);
        if (title.isEmpty()) {
            throw new IllegalArgumentException("A task has a title.");
        }
        if (repeatsAny(projects) || repeatsAny(blockedBy)) {
            throw new IllegalArgumentException("A task names each project and each blocking task once.");
        }
    }

    public Intake keyedOn(WorkKey key) {
        return new Intake(kind, title, projects, key, base, blockedBy, delivery, size);
    }

    public Intake sized(TaskSize itemSize) {
        return new Intake(kind, title, projects, workKey, base, blockedBy, delivery, itemSize);
    }

    public Intake blockedOnlyBy(List<TaskId> blockers) {
        return new Intake(kind, title, projects, workKey, base, blockers, delivery, size);
    }

    public Intake blockedAlsoBy(TaskId blocker) {
        List<TaskId> blockers = new ArrayList<>(blockedBy);
        blockers.add(blocker);
        return new Intake(kind, title, projects, workKey, base, blockers, delivery, size);
    }

    public DeliveryMode deliveryOr(DeliveryMode projectsDelivery) {
        return delivery == null ? projectsDelivery : delivery;
    }

    public boolean namesTheProjectItWorksOn() {
        return kind == TaskKind.CAPTAIN || !projects.isEmpty();
    }

    private static boolean repeatsAny(List<?> names) {
        return new HashSet<>(names).size() < names.size();
    }
}
