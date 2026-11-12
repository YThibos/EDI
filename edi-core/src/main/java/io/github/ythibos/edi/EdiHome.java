package io.github.ythibos.edi;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Component
public final class EdiHome {

    private final Path root;

    public EdiHome(@Value("${edi.home}") Path root) {
        this.root = root;
    }

    public Path root() {
        return root;
    }

    public Path store() {
        return root.resolve("data").resolve("edi.db");
    }

    public Path corePortFile() {
        return root.resolve("state").resolve("core.port");
    }

    public Path coreTokenFile() {
        return root.resolve("state").resolve("core.token");
    }

    public Path spooledRuleFirings() {
        return root.resolve("state").resolve("rules-fired.jsonl");
    }

    public Path corePidFile() {
        return root.resolve("state").resolve("core.pid");
    }

    public Path primaryState() {
        return root.resolve("state").resolve("primary");
    }

    public Path fleetLedger() {
        return root.resolve("state").resolve("fleet-ledger.jsonl");
    }

    public Path projectRegistry() {
        return root.resolve("config").resolve("projects.yaml");
    }

    public Path publishDenyList() {
        return root.resolve("config").resolve("publish-denylist");
    }

    public Path machineProfile() {
        return root.resolve("config").resolve("machine.yaml");
    }

    public Path dispatchRules() {
        return root.resolve("config").resolve("dispatch-rules.json");
    }

    public Path personalConfiguration() {
        return root.resolve("config").resolve("personal.yaml");
    }

    public Path craftRules() {
        return memory().resolve("craft-rules.md");
    }

    public Path memory() {
        return root.resolve("memory");
    }

    public Path worktrees() {
        return root.resolve("worktrees");
    }

    public Path worktreeOf(ProjectName project, TaskId task) {
        return worktrees().resolve(project.value()).resolve(task.value());
    }

    public Path taskDirectory(TaskId task) {
        return root.resolve("data").resolve("tasks").resolve(task.value());
    }

    public Path workerState(WorkerId worker) {
        return root.resolve("state").resolve("workers").resolve(worker.task().value() + "-" + worker.incarnation());
    }
}
