package io.github.ythibos.edi.adapter.registry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.Project;
import io.github.ythibos.edi.domain.project.ProjectName;
import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.yaml.YAMLMapper;

@Component
@RegisterReflectionForBinding({RegisteredProjects.class, RegisteredProject.class, RegisteredTracker.class, RegisteredForgeEntry.class})
public class ProjectRegistry {

    private final Path registry;
    private final Path ediHome;
    private final YAMLMapper yaml = new YAMLMapper();

    public ProjectRegistry(EdiHome home) {
        this.registry = home.projectRegistry();
        this.ediHome = home.root();
    }

    public List<Project> projects() {
        try {
            return registeredProjects().projects().stream().map(RegisteredProject::asProject).toList();
        } catch (JacksonException | IllegalArgumentException unreadable) {
            throw new UnreadableRegistry(registry, unreadable);
        }
    }

    public List<ProjectEntry> entries() {
        try {
            return registeredProjects().projects().stream().map(project -> project.asEntry(ediHome)).toList();
        } catch (JacksonException | IllegalArgumentException unreadable) {
            throw new UnreadableRegistry(registry, unreadable);
        }
    }

    public ProjectEntry entryOf(ProjectName name) {
        RegisteredProject registered = registeredProject(name);
        try {
            return registered.asEntry(ediHome);
        } catch (IllegalArgumentException unreadable) {
            throw new UnreadableRegistry(registry, unreadable);
        }
    }

    private RegisteredProject registeredProject(ProjectName name) {
        try {
            return registeredProjects().projects().stream()
                    .filter(project -> name.value().equals(project.name()))
                    .findFirst()
                    .orElseThrow(() -> new Refusal(name + " is not a registered project; add it to the project registry first."));
        } catch (JacksonException unreadable) {
            throw new UnreadableRegistry(registry, unreadable);
        }
    }

    private RegisteredProjects registeredProjects() {
        try {
            String content = Files.readString(registry);
            return content.isBlank() ? RegisteredProjects.NONE : yaml.readValue(content, RegisteredProjects.class);
        } catch (NoSuchFileException absent) {
            return RegisteredProjects.NONE;
        } catch (IOException unreadable) {
            throw new UnreadableRegistry(registry, unreadable);
        }
    }
}
