package io.github.ythibos.edi.web.call;

import io.github.ythibos.edi.domain.authority.Citation;

record TickRequest(Integer message, Integer line, String text) {

    static final TickRequest UNCITED = new TickRequest(null, null, null);

    Citation citation() {
        return Citation.of(message, line, text);
    }
}
