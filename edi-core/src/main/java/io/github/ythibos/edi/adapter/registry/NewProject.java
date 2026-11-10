package io.github.ythibos.edi.adapter.registry;

import java.util.Objects;

import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.tracker.TrackerKind;

public record NewProject(ProjectName name, RegisteredForge forge, BaseBranch base, DeliveryMode delivery, TrackerKind tracker, ProjectKey key,
        boolean craftRules) {

    public NewProject {
        Objects.requireNonNull(name, "A new project has a name.");
        Objects.requireNonNull(delivery, () -> name + " names how its work is delivered.");
        if (tracker == null && key != null) {
            throw new IllegalArgumentException(name + " keys its work only on a tracker it names.");
        }
    }

    RegisteredProject asRegistered() {
        return new RegisteredProject(name.value(), trackerEntry(), forgeEntry(), null, null, base == null ? null : base.name(), delivery.word(), null,
                craftRules ? Boolean.TRUE : null, null, null, null, null, null, null, null, null);
    }

    private RegisteredTracker trackerEntry() {
        return tracker == null ? null : new RegisteredTracker(tracker.word(), key == null ? null : key.value());
    }

    private RegisteredForgeEntry forgeEntry() {
        return forge == null
                ? null
                : new RegisteredForgeEntry(forge.kind().word(), forge.repository().host(), forge.repository().path(), forge.account());
    }
}
