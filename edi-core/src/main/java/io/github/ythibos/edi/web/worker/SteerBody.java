package io.github.ythibos.edi.web.worker;

import static io.github.ythibos.edi.web.RequestValues.optional;
import static io.github.ythibos.edi.web.RequestValues.required;

import io.github.ythibos.edi.domain.status.StatusKey;

record SteerBody(String text, String resolves) {

    String message() {
        return required(text, "message");
    }

    StatusKey resolvedKey() {
        return optional(resolves, StatusKey::new);
    }
}
