package io.github.ythibos.edi.web.spark;

import static io.github.ythibos.edi.web.RequestValues.required;

import io.github.ythibos.edi.domain.project.WorkKey;

record ItemRequest(String key) {

    WorkKey item() {
        return WorkKey.parse(required(key, "item's key").strip());
    }
}
