package io.github.ythibos.edi.application.migration;

import io.github.ythibos.edi.domain.delivery.RemoteAddress;

record CloneOrigin(boolean cloned, RemoteAddress remote) {

    static CloneOrigin noClone() {
        return new CloneOrigin(false, null);
    }

    static CloneOrigin noRemote() {
        return new CloneOrigin(true, null);
    }

    static CloneOrigin at(RemoteAddress remote) {
        return new CloneOrigin(true, remote);
    }

    boolean hasNoRemote() {
        return cloned && remote == null;
    }
}
