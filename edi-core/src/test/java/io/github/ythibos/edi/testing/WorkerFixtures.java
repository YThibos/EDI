package io.github.ythibos.edi.testing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.application.dispatch.DispatchRequest;
import io.github.ythibos.edi.domain.branch.BranchKind;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.Dispatch;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.mechanical.ExpectedChange;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.domain.worker.WorkerLaunch;
import io.github.ythibos.edi.domain.worker.WorkerWindow;

public final class WorkerFixtures {

    public static final String CRAFT_RULES = """
            Craftsmanship rules this change must meet; review it against them as acceptance criteria:
            - Methods are short and do one thing at one level of abstraction.
            """;
    public static final String MACHINE_PROFILE = """
            workers:
              models:
                - id: claude-opus-5-5
                - id: claude-sonnet-5-5
                - id: claude-haiku-5-5
                  ignoresEffort: true
            """;
    private static final String REGISTRY = """
            projects:
              - name: harness
                tracker: { kind: edi, projectKey: HRN }
                forge: { kind: github, path: example-owner/harness, account: example-owner }
                base: main
                craftRules: true
                aiScaffolding: committed
                verify: "true"
              - name: EDI
                tracker: { kind: edi, projectKey: EDI }
                forge: { kind: github, path: example-owner/EDI, account: example-owner }
                base: main
                craftRules: true
            """;

    private WorkerFixtures() {
    }

    public static TestHome homeWithProjects(Path directory) throws IOException {
        TestHome home = TestHome.in(directory.resolve("home"));
        cloneOf(directory, home, "harness");
        cloneOf(directory, home, "EDI");
        return home.registering(REGISTRY)
                .writing("config/machine.yaml", MACHINE_PROFILE)
                .writing("config/dispatch-rules.json", ExampleHome.file("config/dispatch-rules.json"))
                .writing("memory/craft-rules.md", CRAFT_RULES);
    }

    public static Task queuedShip(String id, String project, String key) {
        return queued(id, TaskKind.SHIP, project, key);
    }

    public static Task queuedShip(String id, String project, String key, DeliveryMode delivery) {
        Intake intake = new Intake(TaskKind.SHIP, "Work on " + id, List.of(new ProjectName(project)), WorkKey.parse(key), null, List.of(), delivery);
        return Task.queued(new TaskId(id), intake, "Do the work on " + id + ".", TestHome.START);
    }

    public static Task queued(String id, TaskKind kind, String project, String key) {
        Intake intake = new Intake(kind, "Work on " + id, List.of(new ProjectName(project)), key == null ? null : WorkKey.parse(key), null, List.of(), null);
        return Task.queued(new TaskId(id), intake, "Do the work on " + id + ".", TestHome.START);
    }

    public static Worker dispatchedShip(TestHome home, String id, String key) {
        home.storing(List.of(queuedShip(id, "harness", key)));
        return home.dispatchTask()
                .dispatch(new TaskReference.ById(new TaskId(id)), new DispatchRequest(CategoryId.IMPLEMENTATION, "", List.of(), null), Actor.PRIMARY)
                .launched()
                .worker();
    }

    public static Worker servingWorkerOn(TestHome home, Task task, CategoryId category) {
        return servingWorkerIn(home.runtime().openWindow(), home, task, category);
    }

    public static Worker servingWorkerIn(WorkerWindow window, TestHome home, Task task, CategoryId category) {
        home.storing(List.of(task));
        home.putInFlight(task.id());
        ExpectedChange expected = category.isMechanical() ? new ExpectedChange("Rename lamp to lantern.", List.of("src/Lamp.java")) : null;
        home.dispatches().save(Dispatch.requested(task.id(), category, BranchName.forWork(BranchKind.FEATURE, task.intake().workKey()), "",
                expected, null, TestHome.START));
        Worker worker = Worker.launched(WorkerId.first(task.id()), UUID.randomUUID(), category,
                new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.HIGH), window,
                new WorkerLaunch(LaunchKind.DISPATCH, TestHome.START, home.machineBoot().current()));
        home.workers().insert(worker, "digest-of-" + task.id());
        return worker;
    }

    public static MergeRequestView opensTheDraftOf(TestHome home, Worker worker) throws IOException {
        TaskId task = worker.id().task();
        ProjectEntry project = home.registry().entryOf(home.tasks().get(task).intake().projects().getFirst());
        BranchName branch = home.dispatches().of(task).orElseThrow().branch();
        String head = FixtureRepository.at(home.home().worktreeOf(project.project().name(), task)).head();
        return home.forge().opensADraft(project.requireForge(), branch, project.requireBase(), head);
    }

    private static void cloneOf(Path directory, TestHome home, String project) throws IOException {
        Path upstream = Files.createDirectories(directory.resolve("upstream").resolve(project));
        FixtureRepository.initialise(upstream)
                .write("README.md", project + "\n")
                .commit("Start " + project)
                .clonedInto(home.home().root().resolve("projects").resolve(project));
    }
}
