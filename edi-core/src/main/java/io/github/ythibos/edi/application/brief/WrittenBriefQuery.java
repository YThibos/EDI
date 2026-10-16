package io.github.ythibos.edi.application.brief;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.task.TaskId;

@Service
public class WrittenBriefQuery {

    private final EdiHome home;
    private final TaskStore tasks;
    private final TransactionTemplate transaction;

    public WrittenBriefQuery(EdiHome home, TaskStore tasks, TransactionTemplate transaction) {
        this.home = home;
        this.tasks = tasks;
        this.transaction = transaction;
    }

    public String briefOf(TaskReference reference) {
        TaskId task = transaction.execute(_ -> tasks.get(reference).id());
        Path brief = home.taskDirectory(task).resolve("brief.md");
        try {
            return Files.readString(brief, StandardCharsets.UTF_8);
        } catch (NoSuchFileException absent) {
            throw new Refusal(task + " has no brief yet; name a category to see the brief a dispatch would write.");
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
