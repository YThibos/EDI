package io.github.ythibos.edi.domain.task;

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
        DeliveryMode delivery) {

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
