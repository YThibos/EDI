package io.github.ythibos.edi.domain.worker;

public record Awaited(boolean message, boolean pipeline) {

    public static final Awaited NOTHING = new Awaited(false, false);

    public boolean isAnything() {
        return message || pipeline;
    }
}
