package io.github.ythibos.edi.application.migration;

import java.nio.file.Path;
import java.util.List;

import io.github.ythibos.edi.adapter.fork.ArchivedFile;
import io.github.ythibos.edi.adapter.fork.ForkHome;

public record ArchivePlan(Path oldHome, String into) {

    List<ArchivedFile> copies() {
        return ForkHome.archiveOf(oldHome).stream().map(file -> new ArchivedFile(file.source(), into + file.path())).toList();
    }
}
