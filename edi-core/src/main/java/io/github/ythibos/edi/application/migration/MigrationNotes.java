package io.github.ythibos.edi.application.migration;

import java.util.ArrayList;
import java.util.List;

final class MigrationNotes {

    private final List<MigrationNote> notes = new ArrayList<>();

    void add(String subject, String note) {
        notes.add(new MigrationNote(subject, note));
    }

    List<MigrationNote> all() {
        return List.copyOf(notes);
    }
}
