package io.github.ythibos.edi.adapter.home;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.home.TrackerSettings.Credentials;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.ModelRoster;
import io.github.ythibos.edi.domain.dispatch.ModelRoster.AllowedModel;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.tracker.TrackerKind;
import io.github.ythibos.edi.domain.tracker.Workflow;
import io.github.ythibos.edi.domain.worker.PermissionMode;
import io.github.ythibos.edi.domain.worker.SupervisionRules;
import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.yaml.YAMLMapper;

@Component
@RegisterReflectionForBinding({MachineProfile.ProfileDocument.class, MachineProfile.WorkersSection.class, MachineProfile.ModelEntry.class,
    MachineProfile.SupervisionSection.class, MachineProfile.TrackerSection.class, MachineProfile.CredentialsSection.class,
    MachineProfile.TimeSection.class})
public class MachineProfile {

    private static final ProfileDocument EMPTY = new ProfileDocument(null, null, null, null, null);
    private static final String UNNAMED = "this machine";

    private final Path file;
    private final YAMLMapper yaml = new YAMLMapper();

    public MachineProfile(EdiHome home) {
        this.file = home.machineProfile();
    }

    public String name() {
        String machine = document().machine();
        return machine == null || machine.isBlank() ? UNNAMED : machine.strip();
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

    public TrackerSettings tracker() {
        TrackerSection tracker = document().tracker();
        if (tracker == null) {
            return TrackerSettings.none();
        }
        try {
            return tracker.settings();
        } catch (IllegalArgumentException invalid) {
            throw new InvalidConfiguration(file, invalid.getMessage());
        }
    }

    public TimeSettings time() {
        TimeSection time = document().time();
        return time == null ? TimeSettings.NONE : new TimeSettings(Boolean.TRUE.equals(time.fleetLedger()), Boolean.TRUE.equals(time.requireWorkedLine()));
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
    record ProfileDocument(String machine, WorkersSection workers, SupervisionSection supervision, TrackerSection tracker, TimeSection time) {
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TrackerSection(
            String kind,
            String baseUrl,
            CredentialsSection credentials,
            Map<String, String> evergreen,
            List<String> workflow,
            String mergedStatus,
            String issueType,
            String epicLinkField) {

        TrackerSettings settings() {
            TrackerKind trackerKind = TrackerKind.named(Objects.requireNonNullElse(kind, TrackerKind.NONE.word()));
            if (trackerKind == TrackerKind.NONE) {
                return TrackerSettings.none();
            }
            return new TrackerSettings(trackerKind, baseUrl == null ? null : URI.create(baseUrl.strip()),
                    credentials == null ? null : credentials.asCredentials(), evergreenKeys(), workflow == null ? null : new Workflow(workflow),
                    mergedStatus, issueType, epicLinkField);
        }

        private Set<WorkKey> evergreenKeys() {
            return Objects.requireNonNullElse(evergreen, Map.<String, String>of()).values().stream().map(WorkKey::parse).collect(Collectors.toSet());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CredentialsSection(String keysFile, String section) {

        Credentials asCredentials() {
            return new Credentials(keysFile == null ? null : Path.of(keysFile), section);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TimeSection(Boolean fleetLedger, Boolean requireWorkedLine) {
    }
}
