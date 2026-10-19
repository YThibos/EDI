package io.github.ythibos.edi.application.migration;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public record HomeSurvey(Path home, List<FileChange> files, ArchiveSurvey archive, List<FileChange> links) {

    public HomeSurvey {
        files = List.copyOf(files);
        links = List.copyOf(links);
    }

    public List<String> replaced() {
        return Stream.concat(files.stream(), links.stream()).filter(file -> file.change() == HomeChange.REPLACED).map(FileChange::path).toList();
    }

    public boolean replacesAnything() {
        return !replaced().isEmpty() || archive.replaced() > 0;
    }
}
