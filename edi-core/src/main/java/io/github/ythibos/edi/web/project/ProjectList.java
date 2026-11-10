package io.github.ythibos.edi.web.project;

import java.util.List;

import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.worker.LoginName;

record ProjectList(List<ListedProject> projects) {

    ProjectList {
        projects = List.copyOf(projects);
    }

    record ListedProject(ProjectName name, ProjectKey workKeys, LoginName login) {
    }
}
