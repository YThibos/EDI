package io.github.ythibos.edi.web.project;

import java.util.List;

import io.github.ythibos.edi.domain.project.Project;

record ProjectList(List<Project> projects) {

    ProjectList {
        projects = List.copyOf(projects);
    }
}
