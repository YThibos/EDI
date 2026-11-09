package io.github.ythibos.edi.web.spark;

import static io.github.ythibos.edi.web.RequestValues.orEmpty;

record DropRequest(String reason) {

    String why() {
        return orEmpty(reason);
    }
}
