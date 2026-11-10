package io.github.ythibos.edi.adapter.registry;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.ythibos.edi.adapter.home.ClaudeLogins;
import io.github.ythibos.edi.adapter.home.HomeRelativePath;
import io.github.ythibos.edi.domain.branch.BranchKind;
import io.github.ythibos.edi.domain.project.AiScaffolding;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.project.Project;
import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.ShipBriefRule;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.tracker.TrackerKind;
import io.github.ythibos.edi.domain.worker.LoginName;

@JsonIgnoreProperties(ignoreUnknown = true)
record RegisteredProject(
        String name,
        RegisteredTracker tracker,
        RegisteredForgeEntry forge,
        @JsonProperty("clone") String clonePath,
        String captainCheckout,
        String base,
        String delivery,
        List<String> branchKinds,
        Boolean craftRules,
        String aiScaffolding,
        Integer capacity,
        String verify,
        String nativeBuild,
        Boolean largeBuilds,
        Boolean ciRuns,
        List<String> shipBriefRules,
        String login) {

    Project asProject() {
        return new Project(new ProjectName(name), workKeys());
    }

    ProjectEntry asEntry(Path ediHome, ClaudeLogins logins) {
        Project project = asProject();
        return new ProjectEntry(
                project,
                trackerKind(),
                clonePath == null ? ediHome.resolve("projects").resolve(name) : HomeRelativePath.expanded(clonePath),
                captainCheckout == null ? null : HomeRelativePath.expanded(captainCheckout),
                base == null ? null : new BaseBranch(base),
                forge == null ? null : forge.asRegisteredForge(),
                delivery == null ? DeliveryMode.DRAFT_REVIEW : DeliveryMode.registryDefault(delivery),
                branchKindsOrAll(),
                Boolean.TRUE.equals(craftRules),
                aiScaffolding == null ? AiScaffolding.ASK : AiScaffolding.named(aiScaffolding),
                capacity,
                commandOrNone(verify),
                commandOrNone(nativeBuild),
                Boolean.TRUE.equals(largeBuilds),
                !Boolean.FALSE.equals(ciRuns),
                Objects.requireNonNullElse(shipBriefRules, List.<String>of()).stream().map(ShipBriefRule::named).toList(),
                loginNamedIn(logins));
    }

    private TrackerKind trackerKind() {
        return tracker == null ? TrackerKind.NONE : tracker.trackerKind();
    }

    private ProjectKey workKeys() {
        return tracker == null || tracker.projectKey() == null ? null : new ProjectKey(tracker.projectKey());
    }

    private Set<BranchKind> branchKindsOrAll() {
        if (branchKinds == null || branchKinds.isEmpty()) {
            return EnumSet.allOf(BranchKind.class);
        }
        return branchKinds.stream().map(BranchKind::named).collect(Collectors.toUnmodifiableSet());
    }

    private LoginName loginNamedIn(ClaudeLogins logins) {
        LoginName named = LoginName.orDefault(login);
        if (!logins.names(named)) {
            throw new IllegalArgumentException(name + " runs on the Claude login " + named + ", which the machine profile does not name under claude.logins.");
        }
        return named;
    }

    private static String commandOrNone(String command) {
        return command == null || command.isBlank() ? null : command.strip();
    }

}
