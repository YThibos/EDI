package io.github.ythibos.edi.web.decision;

record RecheckRequest(String note) {

    String noteOrNone() {
        return note == null ? "" : note;
    }
}
