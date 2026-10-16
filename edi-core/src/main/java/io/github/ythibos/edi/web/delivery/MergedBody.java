package io.github.ythibos.edi.web.delivery;

import static io.github.ythibos.edi.web.RequestValues.required;

record MergedBody(String reference) {

    String requiredReference() {
        return required(reference, "reference").strip();
    }
}
