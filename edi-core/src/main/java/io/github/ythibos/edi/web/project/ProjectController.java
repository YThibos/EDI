package io.github.ythibos.edi.web.project;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.adapter.registry.ProjectRegistry;

@RestController
class ProjectController {

    private final ProjectRegistry registry;

    ProjectController(ProjectRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/api/projects")
    ProjectList projects() {
        return new ProjectList(registry.projects());
    }
}
