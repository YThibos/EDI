package io.github.ythibos.edi.adapter.fork;

import java.util.List;

public record ForkBacklog(List<ForkEntry> entries, List<SetAsideEntry> setAside, List<StrayLine> strayLines) {

    public ForkBacklog {
        entries = List.copyOf(entries);
        setAside = List.copyOf(setAside);
        strayLines = List.copyOf(strayLines);
    }
}
