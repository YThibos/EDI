package io.github.ythibos.edi.application.dispatch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.application.brief.BriefFacts;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.dispatch.Dispatch;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Component
public class GatherBriefFacts {

    private final EdiHome home;
    private final MachineProfile machine;
    private final ResearchBeforeImplementation research;
    private final WorkerRuntime runtime;

    public GatherBriefFacts(EdiHome home, MachineProfile machine, ResearchBeforeImplementation research, WorkerRuntime runtime) {
        this.home = home;
        this.machine = machine;
        this.research = research;
        this.runtime = runtime;
    }

    public BriefFacts factsFor(Task task, ProjectEntry project, Dispatch dispatch, WorkerId worker) {
        return new BriefFacts(task, worker, project, dispatch, baseOf(task, project), home.worktreeOf(project.project().name(), task.id()),
                home.taskDirectory(task.id()), runtime.socket(), craftRulesFor(project), research.planFor(task.id()).orElse(null),
                machine.time().requireWorkedLine());
    }

    private static BaseBranch baseOf(Task task, ProjectEntry project) {
        return task.intake().base() == null ? project.requireBase() : task.intake().base();
    }

    private String craftRulesFor(ProjectEntry project) {
        if (!project.craftRules()) {
            return null;
        }
        try {
            return Files.readString(home.craftRules(), StandardCharsets.UTF_8);
        } catch (NoSuchFileException absent) {
            throw new Refusal(project.project().name() + " follows the craft rules, but " + home.craftRules() + " is missing; write the rules block there first.");
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
