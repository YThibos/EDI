package io.github.ythibos.edi.domain.publish;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record OwnNames(Set<String> names) {

    public OwnNames {
        names = names.stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public static OwnNames none() {
        return new OwnNames(Set.of());
    }

    boolean include(DeniedTerm term) {
        return names.contains(term.term());
    }
}
