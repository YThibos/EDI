package io.github.ythibos.edi.application.migration;

import java.util.List;

public record SetAsideSection(String section, List<String> entries) {

    public SetAsideSection {
        entries = List.copyOf(entries);
    }
}
