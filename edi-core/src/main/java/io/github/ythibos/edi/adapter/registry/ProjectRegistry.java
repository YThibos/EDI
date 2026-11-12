package io.github.ythibos.edi.adapter.registry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.home.ClaudeLogins;
import io.github.ythibos.edi.adapter.home.ConfigurationYaml;
import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.Project;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.rule.Rule;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

@Component
@RegisterReflectionForBinding({RegisteredProjects.class, RegisteredProject.class, RegisteredTracker.class, RegisteredForgeEntry.class})
public class ProjectRegistry {

    private static final String PROJECTS = "projects";
    private static final String ITEM_MARK = "- ";

    private final Path registry;
    private final Path ediHome;
    private final MachineProfile machine;
    private final YAMLMapper yaml = new YAMLMapper();
    private final YAMLMapper writer = ConfigurationYaml.writer();

    public ProjectRegistry(EdiHome home, MachineProfile machine) {
        this.registry = home.projectRegistry();
        this.ediHome = home.root();
        this.machine = machine;
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
            ClaudeLogins logins = machine.claudeLogins();
            return registeredProjects().projects().stream().map(project -> project.asEntry(ediHome, logins)).toList();
        } catch (JacksonException | IllegalArgumentException unreadable) {
            throw new UnreadableRegistry(registry, unreadable);
        }
    }

    public ProjectEntry entryOf(ProjectName name) {
        RegisteredProject registered = registeredProject(name);
        try {
            return registered.asEntry(ediHome, machine.claudeLogins());
        } catch (IllegalArgumentException unreadable) {
            throw new UnreadableRegistry(registry, unreadable);
        }
    }

    public String contentWith(List<NewProject> additions) {
        Optional<String> content = content();
        if (additions.isEmpty() && content.isPresent()) {
            return content.get();
        }
        return content.flatMap(registered -> appendedTo(registered, additions))
                .orElseGet(() -> rendered(content.map(this::documentOf).orElseGet(writer::createObjectNode), additions));
    }

    private RegisteredProject registeredProject(ProjectName name) {
        try {
            return registeredProjects().projects().stream()
                    .filter(project -> name.value().equals(project.name()))
                    .findFirst()
                    .orElseThrow(() -> new Refusal(Rule.TASK_INTAKE, name + " is not a registered project; add it to the project registry first."));
        } catch (JacksonException unreadable) {
            throw new UnreadableRegistry(registry, unreadable);
        }
    }

    private RegisteredProjects registeredProjects() {
        return content().map(registered -> yaml.readValue(registered, RegisteredProjects.class)).orElse(RegisteredProjects.NONE);
    }

    private Optional<String> content() {
        try {
            String content = Files.readString(registry);
            return content.isBlank() ? Optional.empty() : Optional.of(content);
        } catch (NoSuchFileException absent) {
            return Optional.empty();
        } catch (IOException unreadable) {
            throw new UnreadableRegistry(registry, unreadable);
        }
    }

    private Optional<String> appendedTo(String registered, List<NewProject> additions) {
        OptionalInt indent = listIndentIn(registered);
        if (indent.isEmpty()) {
            return Optional.empty();
        }
        String appended = (registered.endsWith("\n") ? registered : registered + "\n") + entriesIndentedBy(indent.getAsInt(), additions);
        return readsAsTheRegistryAnd(appended, registered, additions) ? Optional.of(appended) : Optional.empty();
    }

    private String rendered(ObjectNode document, List<NewProject> additions) {
        ArrayNode projects = document.has(PROJECTS) ? (ArrayNode) document.get(PROJECTS) : document.putArray(PROJECTS);
        additions.forEach(project -> projects.add(writer.valueToTree(project.asRegistered())));
        return writer.writeValueAsString(document);
    }

    private ObjectNode documentOf(String content) {
        try {
            JsonNode document = yaml.readTree(content);
            if (!document.isObject() || document.has(PROJECTS) && !document.get(PROJECTS).isArray()) {
                throw new IllegalArgumentException("it is not a list of projects under projects:");
            }
            return (ObjectNode) document;
        } catch (JacksonException | IllegalArgumentException unreadable) {
            throw new UnreadableRegistry(registry, unreadable);
        }
    }

    private static OptionalInt listIndentIn(String registered) {
        List<String> lines = registered.lines().toList();
        int list = lines.indexOf(PROJECTS + ":");
        return list < 0 ? OptionalInt.empty() : lines.subList(list + 1, lines.size()).stream()
                .filter(line -> line.stripLeading().startsWith(ITEM_MARK))
                .mapToInt(line -> line.length() - line.stripLeading().length())
                .findFirst();
    }

    private String entriesIndentedBy(int indent, List<NewProject> additions) {
        List<String> rendered = rendered(writer.createObjectNode(), additions).lines().skip(1).toList();
        int ownIndent = rendered.getFirst().length() - rendered.getFirst().stripLeading().length();
        return rendered.stream().map(line -> " ".repeat(indent) + line.substring(ownIndent) + "\n").collect(Collectors.joining());
    }

    private boolean readsAsTheRegistryAnd(String appended, String registered, List<NewProject> additions) {
        try {
            List<String> expected = Stream.concat(namesIn(registered).stream(), additions.stream().map(project -> project.name().value())).toList();
            return namesIn(appended).equals(expected);
        } catch (JacksonException unreadable) {
            return false;
        }
    }

    private List<String> namesIn(String registry) {
        return yaml.readValue(registry, RegisteredProjects.class).projects().stream().map(RegisteredProject::name).toList();
    }
}
