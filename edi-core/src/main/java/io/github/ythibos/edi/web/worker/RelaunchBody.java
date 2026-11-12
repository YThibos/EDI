package io.github.ythibos.edi.web.worker;

record RelaunchBody(Boolean fresh) {

    boolean startsFresh() {
        return Boolean.TRUE.equals(fresh);
    }
}
