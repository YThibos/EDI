package io.github.ythibos.edi.web.recovery;

record DropAnswer(Boolean discardWork) {

    boolean discardingWork() {
        return Boolean.TRUE.equals(discardWork);
    }
}
