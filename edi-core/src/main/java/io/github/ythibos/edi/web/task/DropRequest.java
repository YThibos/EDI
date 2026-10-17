package io.github.ythibos.edi.web.task;

record DropRequest(Boolean discardWork) {

    boolean discardingWork() {
        return Boolean.TRUE.equals(discardWork);
    }
}
