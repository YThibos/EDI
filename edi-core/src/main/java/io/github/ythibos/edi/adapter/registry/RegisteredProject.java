package io.github.ythibos.edi.adapter.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.github.ythibos.edi.domain.project.Project;
import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.ProjectName;

@JsonIgnoreProperties(ignoreUnknown = true)
record RegisteredProject(String name, RegisteredTracker tracker) {

    Project asProject() {
        return new Project(new ProjectName(name), workKeys());
    }

    private ProjectKey workKeys() {
        return tracker == null || tracker.projectKey() == null ? null : new ProjectKey(tracker.projectKey());
    }
}
