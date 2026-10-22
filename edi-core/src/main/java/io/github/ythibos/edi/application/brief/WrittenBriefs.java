package io.github.ythibos.edi.application.brief;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;

@Component
public class WrittenBriefs {

    private static final String BRIEF_FILE = "brief.md";

    private final EdiHome home;
    private final WorkerBriefs briefs;

    public WrittenBriefs(EdiHome home, WorkerBriefs briefs) {
        this.home = home;
        this.briefs = briefs;
    }

    public String craftRulesFor(ProjectEntry project) {
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

    public Path written(BriefFacts facts) {
        if (deliversThroughThePipeline(facts)) {
            written(facts.intentPath(), briefs.pipelineIntentFor(facts).text());
        }
        return written(facts.taskDirectory().resolve(BRIEF_FILE), briefs.briefFor(facts));
    }

    public boolean isWrittenFor(TaskId task) {
        return Files.exists(home.taskDirectory(task).resolve(BRIEF_FILE));
    }

    private static boolean deliversThroughThePipeline(BriefFacts facts) {
        return facts.task().intake().kind() == TaskKind.SHIP && facts.delivery() == DeliveryMode.DRAFT_REVIEW;
    }

    private static Path written(Path file, String content) {
        try {
            return Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }
}
