package io.github.ythibos.edi.application.publish;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.application.delivery.Forges;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.publish.OutgoingChanges;
import io.github.ythibos.edi.domain.publish.OwnNames;

@Service
public class PublicationTargets {

    private static final int PROJECT_AND_TASK = 2;

    private final EdiHome home;
    private final Git git;
    private final ProjectRegistry registry;
    private final MachineProfile machine;
    private final Forges forges;

    public PublicationTargets(EdiHome home, Git git, ProjectRegistry registry, MachineProfile machine, Forges forges) {
        this.home = home;
        this.git = git;
        this.registry = registry;
        this.machine = machine;
        this.forges = forges;
    }

    public void requireRegistered(Path repository, String remoteUrl, OutgoingChanges changes) {
        projectCopiedTo(repository).ifPresent(project -> {
            RegisteredForge forge = project.requireForge();
            forge.requirePublicationAt(remoteUrl);
            requireTheWorkflowScope(forge, changes);
        });
    }

    public OwnNames ownNamesOf(Path repository) {
        return projectCopiedTo(repository).map(this::ownNamesOf).orElseGet(OwnNames::none);
    }

    private Optional<ProjectEntry> projectCopiedTo(Path repository) {
        Path copies = realPathOf(home.worktrees());
        Path copy = realPathOf(git.topLevel(repository));
        if (!copy.startsWith(copies) || copies.relativize(copy).getNameCount() != PROJECT_AND_TASK) {
            return Optional.empty();
        }
        return Optional.of(registry.entryOf(new ProjectName(copies.relativize(copy).getName(0).toString())));
    }

    private void requireTheWorkflowScope(RegisteredForge forge, OutgoingChanges changes) {
        if (forge.takesWorkflowChangesIn(changes) && !forges.serving(forge).mayChangeWorkflows(forge)) {
            throw new Refusal("The push changes " + forge.repository().host() + " workflows, which needs the workflow scope on " + forge.account()
                    + "; the captain grants it with gh auth refresh --hostname " + forge.repository().host() + " --user " + forge.account()
                    + " --scopes workflow.");
        }
    }

    private OwnNames ownNamesOf(ProjectEntry project) {
        return new OwnNames(Stream.concat(forgeNamesOf(project), trackerNamesOf(project)).collect(Collectors.toSet()));
    }

    private static Stream<String> forgeNamesOf(ProjectEntry project) {
        return Stream.ofNullable(project.forge()).flatMap(forge -> forge.identifyingNames().stream());
    }

    private Stream<String> trackerNamesOf(ProjectEntry project) {
        return project.tracker().isExternal() ? machine.tracker().identifyingNames().stream() : Stream.empty();
    }

    private static Path realPathOf(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException absent) {
            return path.toAbsolutePath().normalize();
        }
    }
}
