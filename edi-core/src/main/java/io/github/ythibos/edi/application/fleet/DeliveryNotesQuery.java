package io.github.ythibos.edi.application.fleet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.domain.delivery.DeliveryNote;
import io.github.ythibos.edi.domain.task.TaskId;

@Service
public class DeliveryNotesQuery {

    private final EdiHome home;

    public DeliveryNotesQuery(EdiHome home) {
        this.home = home;
    }

    public Map<TaskId, DeliveryNote> notesOf(Collection<TaskId> tasks) {
        Map<TaskId, DeliveryNote> notes = new LinkedHashMap<>();
        tasks.forEach(task -> notes.computeIfAbsent(task, this::noteOf));
        return notes;
    }

    private DeliveryNote noteOf(TaskId task) {
        Path note = home.taskDirectory(task).resolve(TaskLinksQuery.REPORT);
        if (!Files.isRegularFile(note)) {
            return DeliveryNote.BLANK;
        }
        try {
            return DeliveryNote.of(Files.readString(note));
        } catch (IOException unreadable) {
            return DeliveryNote.BLANK;
        }
    }
}
