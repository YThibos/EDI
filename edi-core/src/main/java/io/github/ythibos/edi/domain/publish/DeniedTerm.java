package io.github.ythibos.edi.domain.publish;

import java.util.Locale;

public record DeniedTerm(int entry, String term) {

    public DeniedTerm {
        term = term.toLowerCase(Locale.ROOT);
    }

    boolean appearsIn(String text) {
        return text.toLowerCase(Locale.ROOT).contains(term);
    }
}
