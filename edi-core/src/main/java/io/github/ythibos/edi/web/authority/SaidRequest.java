package io.github.ythibos.edi.web.authority;

import static io.github.ythibos.edi.web.RequestValues.optional;
import static io.github.ythibos.edi.web.RequestValues.required;

import io.github.ythibos.edi.domain.decision.TaskReference;

record SaidRequest(String text, String task) {

    String words() {
        return required(text, "text, the captain's own words");
    }

    TaskReference concernedTask() {
        return optional(task, TaskReference::parse);
    }
}
