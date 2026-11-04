package io.github.ythibos.edi.web.task;

import io.github.ythibos.edi.domain.authority.Citation;
import io.github.ythibos.edi.domain.project.WorkKey;

record KeyRequest(String key, Integer message, Integer line, String text) {

    WorkKey workKey() {
        if (key == null) {
            throw new IllegalArgumentException("A task is keyed on the work key it books its work on.");
        }
        return WorkKey.parse(key);
    }

    Citation citation() {
        return message == null && text == null && line == null ? null : Citation.of(message, line, text);
    }
}
