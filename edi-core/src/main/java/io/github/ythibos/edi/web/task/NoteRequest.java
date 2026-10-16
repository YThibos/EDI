package io.github.ythibos.edi.web.task;

import static io.github.ythibos.edi.web.RequestValues.required;

record NoteRequest(String note) {

    String text() {
        return required(note, "note");
    }
}
