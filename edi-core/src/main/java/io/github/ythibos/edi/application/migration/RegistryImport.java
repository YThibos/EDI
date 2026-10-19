package io.github.ythibos.edi.application.migration;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.ythibos.edi.adapter.fork.ForkHome;
import io.github.ythibos.edi.adapter.fork.ForkProject;
import io.github.ythibos.edi.adapter.registry.NewProject;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.delivery.RemoteAddress;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.tracker.TrackerKind;

final class RegistryImport {

    static final String REGISTRY = "config/projects.yaml";
    static final ProjectName EDI = new ProjectName("EDI");
    private static final ProjectKey EDI_KEY = new ProjectKey("EDI");
    private static final String GITHUB_HOST = "github.com";
    private static final String NAMESPACE_END = "/";

    private final List<ProjectEntry> registered;
    private final Set<String> craftRuleProjects;
    private final MigrationNotes notes;

    RegistryImport(List<ProjectEntry> registered, List<String> craftRuleProjects, MigrationNotes notes) {
        this.registered = List.copyOf(registered);
        this.craftRuleProjects = Set.copyOf(craftRuleProjects);
        this.notes = notes;
    }

    List<NewProject> additions(List<ForkProject> projects, Function<String, CloneOrigin> origins) {
        Set<String> known = registered.stream().map(entry -> entry.project().name().value()).collect(Collectors.toSet());
        return projects.stream()
                .filter(project -> !project.name().equals(ForkHome.HARNESS_PROJECT) && !known.contains(project.name()))
                .map(project -> registryEntryOf(project, origins.apply(project.name())))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<NewProject> registryEntryOf(ForkProject project, CloneOrigin origin) {
        try {
            ProjectName name = new ProjectName(project.name());
            RegisteredForge forge = forgeOf(name, origin);
            BaseBranch base = baseOf(name, project.base());
            DeliveryMode delivery = deliveryOf(name, origin);
            TrackerKind tracker = trackerOf(name);
            return Optional.of(new NewProject(name, forge, base, delivery, tracker, tracker == TrackerKind.EDI ? EDI_KEY : null,
                    craftRuleProjects.contains(project.name())));
        } catch (IllegalArgumentException unusable) {
            notes.add(project.name(), "is not registered: " + unusable.getMessage());
            return Optional.empty();
        }
    }

    private RegisteredForge forgeOf(ProjectName name, CloneOrigin origin) {
        if (!origin.cloned()) {
            notes.add(name.value(), "has no clone in the old home, so EDI does not know its forge; add forge: to its entry in " + REGISTRY);
            return null;
        }
        return origin.remote() == null ? null : registeredForgeAt(name, origin.remote());
    }

    private BaseBranch baseOf(ProjectName name, String base) {
        if (base == null) {
            notes.add(name.value(), "names no base branch; add base: to its entry in " + REGISTRY);
            return null;
        }
        return new BaseBranch(base);
    }

    private DeliveryMode deliveryOf(ProjectName name, CloneOrigin origin) {
        if (origin.hasNoRemote()) {
            notes.add(name.value(), "has no remote, so its work arrives as a branch in your checkout; add captainCheckout: to its entry in " + REGISTRY);
            return DeliveryMode.LOCAL_BRANCH;
        }
        return DeliveryMode.DRAFT_REVIEW;
    }

    private TrackerKind trackerOf(ProjectName name) {
        if (name.equals(EDI)) {
            return TrackerKind.EDI;
        }
        notes.add(name.value(), "names no tracker, so its tasks get no work key and no branch; add tracker: { kind: jira, projectKey: <KEY> }"
                + " or { kind: edi, projectKey: <KEY> } to its entry in " + REGISTRY);
        return null;
    }

    private RegisteredForge registeredForgeAt(ProjectName name, RemoteAddress address) {
        ForgeRepository repository = new ForgeRepository(forgeKindOf(address), address.host(), address.path());
        Optional<String> account = accountOn(address);
        if (account.isEmpty()) {
            notes.add(name.value(), "lives at " + repository + ", but no registered project there names the account EDI works under;"
                    + " add forge: { kind: " + repository.kind().word() + ", host: " + repository.host() + ", path: " + repository.path()
                    + ", account: <account> } to its entry in " + REGISTRY);
            return null;
        }
        return new RegisteredForge(repository, account.get());
    }

    private static ForgeKind forgeKindOf(RemoteAddress address) {
        return address.host().equalsIgnoreCase(GITHUB_HOST) ? ForgeKind.GITHUB : ForgeKind.GITLAB;
    }

    private Optional<String> accountOn(RemoteAddress address) {
        return registered.stream()
                .map(ProjectEntry::forge)
                .filter(forge -> forge != null && forge.repository().host().equalsIgnoreCase(address.host())
                        && namespaceOf(forge.repository().path()).equalsIgnoreCase(namespaceOf(address.path())))
                .map(RegisteredForge::account)
                .findFirst();
    }

    private static String namespaceOf(String path) {
        return path.substring(0, path.indexOf(NAMESPACE_END) + 1);
    }
}
