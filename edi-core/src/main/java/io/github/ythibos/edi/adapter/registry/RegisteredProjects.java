package io.github.ythibos.edi.adapter.registry;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record RegisteredProjects(List<RegisteredProject> projects) {

    static final RegisteredProjects NONE = new RegisteredProjects(List.of());

    RegisteredProjects {
        projects = projects == null ? List.of() : List.copyOf(projects);
    }
}
