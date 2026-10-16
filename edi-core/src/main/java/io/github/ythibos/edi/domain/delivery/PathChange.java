package io.github.ythibos.edi.domain.delivery;

import java.util.Locale;

public enum PathChange {
    ADDED(false),
    MODIFIED(false),
    DELETED(true),
    RENAMED(true),
    MODE_CHANGED(true);

    private final boolean destructive;

    PathChange(boolean destructive) {
        this.destructive = destructive;
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    boolean isDestructive() {
        return destructive;
    }
}
