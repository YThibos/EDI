package io.github.ythibos.edi.web.project;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.web.project.ProjectList.ListedProject;

@RestController
class ProjectController {

    private final ProjectRegistry registry;

    ProjectController(ProjectRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/api/projects")
    ProjectList projects() {
        return new ProjectList(registry.entries().stream().map(ProjectController::listed).toList());
    }

    private static ListedProject listed(ProjectEntry entry) {
        return new ListedProject(entry.project().name(), entry.project().workKeys(), entry.login());
    }
}
