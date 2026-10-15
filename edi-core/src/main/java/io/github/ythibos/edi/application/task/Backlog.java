package io.github.ythibos.edi.application.task;

import java.util.List;

public record Backlog(boolean readyOnly, List<BacklogEntry> entries) {

    public Backlog {
        entries = List.copyOf(entries);
    }
}
