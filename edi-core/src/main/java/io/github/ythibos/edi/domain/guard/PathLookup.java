package io.github.ythibos.edi.domain.guard;

import java.util.List;

@FunctionalInterface
public interface PathLookup {

    PathLookup AS_WRITTEN = _ -> List.of();

    List<String> pathsOf(String word);
}
