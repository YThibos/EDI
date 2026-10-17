package io.github.ythibos.edi.domain.tracker;

import java.util.Objects;
import java.util.Set;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;

public record TrackedProject(ProjectName name, TrackerKind tracker, ProjectKey key, Set<WorkKey> evergreen) {

    public TrackedProject {
        Objects.requireNonNull(name, "A tracked project has its registry name.");
        Objects.requireNonNull(tracker, () -> name + " names the tracker it books work on.");
        evergreen = tracker.isExternal() ? Set.copyOf(evergreen) : Set.of();
        if (tracker != TrackerKind.NONE && key == null) {
            throw new Refusal(name + " books its work on " + tracker.word() + " but names no projectKey in the project registry.");
        }
    }

    public void requireOwns(WorkKey workKey) {
        if (tracker == TrackerKind.NONE || workKey.project().equals(key) || isEvergreen(workKey)) {
            return;
        }
        throw new Refusal(workKey + " is not a " + name + " key: its work is keyed " + key + "-<n>"
                + (evergreen.isEmpty() ? "." : " or on an evergreen ticket."));
    }

    public boolean isEvergreen(WorkKey workKey) {
        return evergreen.contains(workKey);
    }
}
