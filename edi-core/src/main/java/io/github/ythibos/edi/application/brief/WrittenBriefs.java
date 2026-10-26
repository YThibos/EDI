package io.github.ythibos.edi.application.brief;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.domain.task.TaskId;

@Component
public class WrittenBriefs {

    private static final String BRIEF_FILE = "brief.md";

    private final EdiHome home;
    private final WorkerBriefs briefs;

    public WrittenBriefs(EdiHome home, WorkerBriefs briefs) {
        this.home = home;
        this.briefs = briefs;
    }

    public Path written(BriefFacts facts) {
        if (facts.deliversThroughThePipeline()) {
            written(facts.intentPath(), briefs.pipelineIntentFor(facts).text());
        }
        return written(facts.taskDirectory().resolve(BRIEF_FILE), briefs.briefFor(facts));
    }

    public boolean isWrittenFor(TaskId task) {
        return Files.exists(home.taskDirectory(task).resolve(BRIEF_FILE));
    }

    private static Path written(Path file, String content) {
        try {
            return Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }
}
