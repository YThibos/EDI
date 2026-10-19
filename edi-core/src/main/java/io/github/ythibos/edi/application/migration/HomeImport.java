package io.github.ythibos.edi.application.migration;

import java.util.List;

public record HomeImport(List<HomeFile> files, ArchivePlan archive, List<FolderLink> links, List<MemoryRoute> memory) {

    public HomeImport {
        files = List.copyOf(files);
        links = List.copyOf(links);
        memory = List.copyOf(memory);
    }
}
