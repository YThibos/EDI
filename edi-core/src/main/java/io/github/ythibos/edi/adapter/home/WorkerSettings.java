package io.github.ythibos.edi.adapter.home;

import java.util.List;
import java.util.Objects;

import io.github.ythibos.edi.domain.dispatch.ModelRoster;
import io.github.ythibos.edi.domain.worker.PermissionMode;

public record WorkerSettings(ModelRoster models, List<String> disallowedTools, PermissionMode permissionMode) {

    public WorkerSettings {
        Objects.requireNonNull(models, "The machine profile lists the models workers may run.");
        disallowedTools = List.copyOf(disallowedTools);
        Objects.requireNonNull(permissionMode, "The machine profile names how workers run their tools.");
    }
}
