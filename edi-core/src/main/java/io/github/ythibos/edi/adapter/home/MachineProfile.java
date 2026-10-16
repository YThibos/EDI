package io.github.ythibos.edi.adapter.home;

import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.ModelRoster;
import io.github.ythibos.edi.domain.dispatch.ModelRoster.AllowedModel;
import io.github.ythibos.edi.domain.worker.PermissionMode;
import io.github.ythibos.edi.domain.worker.SupervisionRules;
import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.yaml.YAMLMapper;

@Component
@RegisterReflectionForBinding({MachineProfile.ProfileDocument.class, MachineProfile.WorkersSection.class, MachineProfile.ModelEntry.class,
    MachineProfile.SupervisionSection.class})
public class MachineProfile {

    private static final ProfileDocument EMPTY = new ProfileDocument(null, null);

    private final Path file;
    private final YAMLMapper yaml = new YAMLMapper();

    public MachineProfile(EdiHome home) {
        this.file = home.machineProfile();
    }

    public WorkerSettings workers() {
        WorkersSection workers = Objects.requireNonNullElse(document().workers(), WorkersSection.NONE);
        try {
            return new WorkerSettings(roster(workers), disallowedTools(workers), permissionMode(workers));
        } catch (IllegalArgumentException invalid) {
            throw new InvalidConfiguration(file, invalid.getMessage());
        }
    }

    public SupervisionRules supervision() {
        SupervisionSection supervision = Objects.requireNonNullElse(document().supervision(), SupervisionSection.NONE);
        try {
            return new SupervisionRules(
                    durationOf(supervision.staleAfter(), SupervisionRules.STALE_AFTER),
                    durationOf(supervision.pauseResurface(), SupervisionRules.PAUSE_RESURFACE),
                    SupervisionRules.BUSY_CEILING);
        } catch (DateTimeParseException invalid) {
            throw new InvalidConfiguration(file, "supervision holds \"" + invalid.getParsedString()
                    + "\", which is not a duration such as PT20M.");
        }
    }

    private ProfileDocument document() {
        try {
            return ConfigurationFile.contentOf(file).map(content -> yaml.readValue(content, ProfileDocument.class)).orElse(EMPTY);
        } catch (JacksonException unreadable) {
            throw new InvalidConfiguration(file, unreadable.getOriginalMessage());
        }
    }

    private static ModelRoster roster(WorkersSection workers) {
        return new ModelRoster(Objects.requireNonNullElse(workers.models(), List.<ModelEntry>of()).stream()
                .map(model -> new AllowedModel(new ModelId(model.id()), !Boolean.TRUE.equals(model.ignoresEffort())))
                .toList());
    }

    private static List<String> disallowedTools(WorkersSection workers) {
        List<String> tools = Objects.requireNonNullElse(workers.disallowedTools(), List.of());
        tools.stream().filter(tool -> !ToolName.isWellFormed(tool)).findFirst().ifPresent(malformed -> {
            throw new IllegalArgumentException("workers.disallowedTools holds \"" + malformed
                    + "\", which is not a tool name such as WebSearch or Bash(git push:*).");
        });
        return tools;
    }

    private static PermissionMode permissionMode(WorkersSection workers) {
        return workers.permissionMode() == null ? PermissionMode.BYPASS : PermissionMode.named(workers.permissionMode());
    }

    private static Duration durationOf(String written, Duration absent) {
        return written == null ? absent : Duration.parse(written);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProfileDocument(WorkersSection workers, SupervisionSection supervision) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WorkersSection(List<ModelEntry> models, List<String> disallowedTools, String permissionMode) {

        static final WorkersSection NONE = new WorkersSection(null, null, null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ModelEntry(String id, Boolean ignoresEffort) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SupervisionSection(String staleAfter, String pauseResurface) {

        static final SupervisionSection NONE = new SupervisionSection(null, null);
    }
}
