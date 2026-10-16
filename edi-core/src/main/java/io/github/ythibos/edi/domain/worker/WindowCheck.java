package io.github.ythibos.edi.domain.worker;

public enum WindowCheck {
    OPEN,
    WINDOW_CLOSED,
    SERVER_CHANGED,
    SERVER_GONE;

    public boolean isOpen() {
        return this == OPEN;
    }
}
