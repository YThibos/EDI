package io.github.ythibos.edi.web.migration;

record MigrationRequest(String from, Boolean overwrite) {

    boolean overwriting() {
        return Boolean.TRUE.equals(overwrite);
    }
}
