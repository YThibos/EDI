package io.github.ythibos.edi.application.migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.fork.ForkEnvironment;
import io.github.ythibos.edi.adapter.fork.ForkHome;
import io.github.ythibos.edi.adapter.fork.ForkProject;
import io.github.ythibos.edi.adapter.fork.ForkRoutingRulesReader;
import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.home.CarriedMachineSettings;
import io.github.ythibos.edi.adapter.home.DispatchRulesFile;
import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.home.PersonalConfiguration;
import io.github.ythibos.edi.adapter.registry.NewProject;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.delivery.RemoteAddress;
import io.github.ythibos.edi.domain.status.CaptainAddress;
import io.github.ythibos.edi.domain.task.Task;

@Component
public class HomeImportPlanner {

    static final String ARCHIVE = "data/archive/firstofficer/";
    private static final String MACHINE_PROFILE = "config/machine.yaml";
    private static final String PERSONAL_CONFIGURATION = "config/personal.yaml";
    private static final String CRAFT_RULES = "memory/craft-rules.md";
    private static final String TASK_FOLDERS = "data/tasks/";
    private static final String OLD_FOLDER_LINK = "/firstofficer";
    private static final String OLD_FOLDER_TARGET = "../../archive/firstofficer/tasks/";
    private static final String ORIGIN = "origin";

    private final EdiHome home;
    private final ProjectRegistry registry;
    private final MachineProfile machine;
    private final PersonalConfiguration personal;
    private final DispatchRulesFile dispatchRules;
    private final ExampleConfiguration examples;
    private final ForkRoutingRulesReader routingRules;
    private final ForkEnvironment oldEnvironment;
    private final Git git;

    public HomeImportPlanner(EdiHome home, ProjectRegistry registry, MachineProfile machine, PersonalConfiguration personal,
            DispatchRulesFile dispatchRules, ExampleConfiguration examples, ForkRoutingRulesReader routingRules, ForkEnvironment oldEnvironment,
            Git git) {
        this.home = home;
        this.registry = registry;
        this.machine = machine;
        this.personal = personal;
        this.dispatchRules = dispatchRules;
        this.examples = examples;
        this.routingRules = routingRules;
        this.oldEnvironment = oldEnvironment;
        this.git = git;
    }

    HomeImport planFor(ForkHome old, List<Task> importedTasks, MigrationNotes notes) {
        requireApart(old);
        List<ProjectEntry> registered = registry.entries();
        Map<String, CloneOrigin> origins = originsOfTheClonesIn(old);
        List<NewProject> newProjects = new RegistryImport(registered, old.craftRulesProjects(), notes).additions(old.projects(), origins::get);
        MemoryImport memory = MemoryImport.of(old, projectsOfThisMachine(old));
        List<HomeFile> files = new ArrayList<>(List.of(
                registryWith(registered, newProjects),
                machineProfileFrom(old, notes),
                personalConfiguration(),
                dispatchRulesFrom(old, notes),
                denyListFor(registered, newProjects, origins.values())));
        craftRulesFrom(old, notes).ifPresent(files::add);
        files.addAll(memory.files(old));
        return new HomeImport(files, new ArchivePlan(old.root(), ARCHIVE), linksTo(old, importedTasks), memory.routes());
    }

    private void requireApart(ForkHome old) {
        Path oldRoot = old.root().toAbsolutePath().normalize();
        Path ediRoot = home.root().toAbsolutePath().normalize();
        if (oldRoot.startsWith(ediRoot) || ediRoot.startsWith(oldRoot)) {
            throw new IllegalArgumentException("The old home " + oldRoot + " and the EDI home " + ediRoot + " overlap; each must lie outside the other.");
        }
    }

    private Map<String, CloneOrigin> originsOfTheClonesIn(ForkHome old) {
        return old.projects().stream().collect(Collectors.toMap(ForkProject::name, project -> originOf(old.projectClone(project.name())),
                (first, _) -> first));
    }

    private HomeFile registryWith(List<ProjectEntry> registered, List<NewProject> newProjects) {
        return new HomeFile(RegistryImport.REGISTRY, registry.contentWith(newProjects), Counted.projects(registered.size() + newProjects.size()));
    }

    private HomeFile machineProfileFrom(ForkHome old, MigrationNotes notes) {
        if (!machine.namesATracker()) {
            notes.add(MACHINE_PROFILE, "names no tracker; on a machine that books its work on JIRA, add the tracker section");
        }
        CarriedMachineSettings carried = new CarriedMachineSettings(
                MachineProfile.workerRosterIn(examples.machineProfile()),
                old.keepsAFleetLedger() ? Boolean.TRUE : null,
                old.startupMemoryBudget().isPresent() ? old.startupMemoryBudget().getAsInt() : null,
                oldEnvironment.pauseResurface().orElse(null));
        return new HomeFile(MACHINE_PROFILE, machine.contentWith(carried), "the worker models and the settings the old home kept");
    }

    private HomeFile personalConfiguration() {
        return new HomeFile(PERSONAL_CONFIGURATION, personal.contentAddressing(CaptainAddress.DEFAULT), "the address word");
    }

    private HomeFile dispatchRulesFrom(ForkHome old, MigrationNotes notes) {
        RoutingRulesImport routing = RoutingRulesImport.of(old.routingRules().map(routingRules::read).orElse(List.of()), notes);
        return new HomeFile(RoutingRulesImport.DISPATCH_RULES,
                dispatchRules.withWhatItLacks(currentContentOf(RoutingRulesImport.DISPATCH_RULES).orElseGet(examples::dispatchRules),
                        examples.dispatchRules(), routing.reasonsByCategory(), routing.reasonsByProject()),
                "the dispatch categories and overrides, with the old reasons they lacked");
    }

    private HomeFile denyListFor(List<ProjectEntry> registered, List<NewProject> newProjects, Collection<CloneOrigin> origins) {
        return DenyListImport.listing(currentContentOf(DenyListImport.DENY_LIST), forgesOf(registered, newProjects),
                origins.stream().map(CloneOrigin::remote).filter(Objects::nonNull).toList(), machine.tracker());
    }

    private Optional<HomeFile> craftRulesFrom(ForkHome old, MigrationNotes notes) {
        Optional<String> rules = old.craftRules();
        if (rules.isEmpty() && currentContentOf(CRAFT_RULES).isEmpty()) {
            notes.add(CRAFT_RULES, "is missing and the old home holds no craft rules; write the rules block there before a craft-rules project's first ship");
        }
        return rules.map(text -> new HomeFile(CRAFT_RULES, text, "the craftsmanship rules block"));
    }

    private static List<FolderLink> linksTo(ForkHome old, List<Task> importedTasks) {
        return importedTasks.stream()
                .filter(task -> !task.isClosed() && Files.isDirectory(old.root().resolve("data").resolve(task.id().value())))
                .map(task -> new FolderLink(task.id(), TASK_FOLDERS + task.id() + OLD_FOLDER_LINK, OLD_FOLDER_TARGET + task.id()))
                .toList();
    }

    private CloneOrigin originOf(Path clone) {
        if (!isAClone(clone)) {
            return CloneOrigin.noClone();
        }
        return git.fetchUrlOf(clone, ORIGIN).flatMap(RemoteAddress::of).map(CloneOrigin::at).orElseGet(CloneOrigin::noRemote);
    }

    private static Set<String> projectsOfThisMachine(ForkHome old) {
        return old.projects().stream()
                .map(ForkProject::name)
                .filter(name -> !name.equals(ForkHome.HARNESS_PROJECT) && !name.equals(RegistryImport.EDI.value()))
                .collect(Collectors.toSet());
    }

    private Optional<String> currentContentOf(String file) {
        try {
            return Optional.of(Files.readString(home.root().resolve(file)));
        } catch (NoSuchFileException absent) {
            return Optional.empty();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static List<RegisteredForge> forgesOf(List<ProjectEntry> registered, List<NewProject> newProjects) {
        return Stream.concat(registered.stream().map(ProjectEntry::forge), newProjects.stream().map(NewProject::forge)).filter(Objects::nonNull).toList();
    }

    private boolean isAClone(Path clone) {
        try {
            return git.isRepository(clone) && git.topLevel(clone).equals(clone.toRealPath());
        } catch (IOException unresolvable) {
            return false;
        }
    }
}
